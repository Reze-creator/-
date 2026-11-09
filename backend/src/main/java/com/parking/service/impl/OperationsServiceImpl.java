package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BusinessException;
import com.parking.common.Pages;
import com.parking.common.Types.Role;
import com.parking.entity.AuditEntity;
import com.parking.entity.BookingEntity;
import com.parking.entity.DeviceEntity;
import com.parking.entity.DeviceEventEntity;
import com.parking.entity.IncidentEntity;
import com.parking.entity.UsageBillEntity;
import com.parking.entity.UsageSessionEntity;
import com.parking.mapper.AuditMapper;
import com.parking.mapper.BookingMapper;
import com.parking.mapper.DeviceEventMapper;
import com.parking.mapper.DeviceMapper;
import com.parking.mapper.IncidentMapper;
import com.parking.mapper.OutboxMapper;
import com.parking.mapper.ResourceMapper;
import com.parking.mapper.UsageBillMapper;
import com.parking.mapper.UsageSessionMapper;
import com.parking.service.OperationsService;
import com.parking.service.OutboxService;
import com.parking.vo.AuditView;
import com.parking.vo.IncidentView;
import com.parking.vo.UserView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 运营工单是可追踪的异常记录，人工关闭前必须先恢复对应业务条件。 */
@Service
public class OperationsServiceImpl implements OperationsService {
  private final IncidentMapper incidents;
  private final AuditMapper audits;
  private final DeviceMapper devices;
  private final DeviceEventMapper events;
  private final UsageSessionMapper sessions;
  private final UsageBillMapper bills;
  private final BookingMapper bookings;
  private final ResourceMapper resources;
  private final Clock clock;
  private final MeterRegistry metrics;
  private final long offlineSeconds;
  private final OutboxService outbox;
  private final OutboxMapper outboxMapper;

  public OperationsServiceImpl(
      IncidentMapper incidents,
      AuditMapper audits,
      DeviceMapper devices,
      DeviceEventMapper events,
      UsageSessionMapper sessions,
      UsageBillMapper bills,
      BookingMapper bookings,
      ResourceMapper resources,
      Clock clock,
      MeterRegistry metrics,
      OutboxService outbox,
      OutboxMapper outboxMapper,
      @Value("${parking.device-offline-seconds:120}") long offlineSeconds) {
    this.incidents = incidents;
    this.audits = audits;
    this.devices = devices;
    this.events = events;
    this.sessions = sessions;
    this.bills = bills;
    this.bookings = bookings;
    this.resources = resources;
    this.clock = clock;
    this.metrics = metrics;
    this.offlineSeconds = offlineSeconds;
    this.outbox = outbox;
    this.outboxMapper = outboxMapper;
  }

  @Override
  @Transactional
  public void report(
      String type,
      String sourceId,
      String resourceId,
      String bookingId,
      String title,
      String detail) {
    IncidentEntity previous =
        incidents.selectOne(
            new QueryWrapper<IncidentEntity>().eq("source_type", type).eq("source_id", sourceId));
    // 定时扫描重复看到同一未恢复异常时，不制造新的索引消息或递增无意义版本。
    if (previous != null
        && "OPEN".equals(previous.getStatus())
        && Objects.equals(title, previous.getTitle())
        && Objects.equals(detail, previous.getDetail())) return;
    IncidentEntity incident = new IncidentEntity();
    incident.setId(UUID.randomUUID().toString());
    incident.setSourceType(type);
    incident.setSourceId(sourceId);
    incident.setResourceId(resourceId);
    incident.setBookingId(bookingId);
    incident.setTitle(title);
    incident.setDetail(detail);
    incident.setCreatedAt(clock.instant());
    incidents.reopen(incident);
    IncidentEntity saved =
        incidents.selectOne(
            new QueryWrapper<IncidentEntity>().eq("source_type", type).eq("source_id", sourceId));
    outbox.enqueue("INCIDENT_CHANGED", saved.getId());
  }

  @Override
  public void audit(UserView actor, String action, String targetId, String detail) {
    AuditEntity audit = new AuditEntity();
    audit.setId(UUID.randomUUID().toString());
    audit.setActorId(actor.id());
    audit.setAction(action);
    audit.setTargetId(targetId);
    audit.setDetail(detail);
    audit.setRequestId(Optional.ofNullable(MDC.get("requestId")).orElse("internal"));
    audit.setCreatedAt(clock.instant());
    audits.insert(audit);
  }

  @Override
  public Pages<IncidentView> incidents(String status, int page, int size) {
    if (status != null && !Set.of("OPEN", "RESOLVED").contains(status))
      throw BusinessException.invalid("工单状态必须为 OPEN 或 RESOLVED");
    return Pages.select(
        incidents,
        new QueryWrapper<IncidentEntity>().eq(status != null, "status", status),
        page,
        size,
        this::view);
  }

  @Override
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public IncidentView resolve(UserView actor, String id, String resolution) {
    if (actor.role() != Role.ADMIN) throw BusinessException.forbidden();
    IncidentEntity incident = incidents.selectById(id);
    if (incident == null) throw BusinessException.missing("工单不存在");
    // 与事件、心跳共用资源锁，避免检查恢复后故障又被同时写入。
    lockIncidentResource(incident.getResourceId());
    incident = incidents.selectById(id);
    if ("RESOLVED".equals(incident.getStatus())) return view(incident);
    requireRecovered(incident);
    incident.setStatus("RESOLVED");
    incident.setResolution(resolution.trim());
    incident.setResolvedBy(actor.id());
    incident.setResolvedAt(clock.instant());
    incident.setSearchVersion(incident.getSearchVersion() + 1);
    incidents.updateById(incident);
    outbox.enqueue("INCIDENT_CHANGED", incident.getId());
    audit(actor, "RESOLVE_INCIDENT", id, resolution.trim());
    return view(incident);
  }

  private void lockIncidentResource(String resourceId) {
    if (resourceId != null) resources.lock(resourceId);
  }

  private void requireRecovered(IncidentEntity incident) {
    if ("MESSAGE_FAILURE".equals(incident.getSourceType())) {
      com.parking.entity.OutboxEntity message = outboxMapper.selectById(incident.getSourceId());
      if (message == null || !"DONE".equals(message.getStatus()))
        throw BusinessException.conflict("请先恢复依赖并重试消息，待其完成后再关单");
    } else if (incident.getSourceType().startsWith("DEVICE_")) {
      DeviceEntity device = devices.selectById(incident.getResourceId());
      if (device == null
          || Boolean.TRUE.equals(device.getFault())
          || device.getLastSeenAt() == null
          || !device.getLastSeenAt().isAfter(clock.instant().minusSeconds(offlineSeconds)))
        throw BusinessException.conflict("请先发送正常心跳恢复设备");
    } else if (incident.getSourceType().startsWith("EVENT_")) {
      DeviceEventEntity event = events.selectById(incident.getSourceId());
      if (event == null || !Set.of("APPLIED", "SKIPPED").contains(event.getStatus()))
        throw BusinessException.conflict("请先补齐缺失事件，或重试与跳过阻塞事件");
    } else {
      UsageSessionEntity session = sessions.selectById(incident.getSourceId());
      if (session == null || !"FINISHED".equals(session.getStatus()))
        throw BusinessException.conflict("请先处理会话并完成离场");
    }
  }

  @Override
  public Pages<AuditView> audits(int page, int size) {
    return Pages.select(
        audits,
        new QueryWrapper<>(),
        page,
        size,
        audit ->
            new AuditView(
                audit.getId(),
                audit.getActorId(),
                audit.getAction(),
                audit.getTargetId(),
                audit.getDetail(),
                audit.getRequestId(),
                audit.getCreatedAt()));
  }

  @Override
  public Map<String, Object> overview() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(
        "openIncidents",
        incidents.selectCount(new QueryWrapper<IncidentEntity>().eq("status", "OPEN")));
    data.put(
        "activeSessions",
        sessions.selectCount(new QueryWrapper<UsageSessionEntity>().ne("status", "FINISHED")));
    data.put(
        "waitingEvents",
        events.selectCount(new QueryWrapper<DeviceEventEntity>().eq("status", "WAITING")));
    data.put(
        "failedEvents",
        events.selectCount(new QueryWrapper<DeviceEventEntity>().eq("status", "FAILED")));
    data.put(
        "settledBills",
        bills.selectCount(new QueryWrapper<UsageBillEntity>().eq("status", "SIMULATED_SETTLED")));
    data.put("sampledAt", clock.instant());
    // 标签只用路由模板，避免为每一个预约 ID 创建一个指标。
    data.put("http", metrics.find("parking.api").timers().stream().map(this::timerView).toList());
    data.put(
        "heapUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    return data;
  }

  private Map<String, Object> timerView(Timer timer) {
    return Map.of(
        "route",
        Objects.toString(timer.getId().getTag("route"), "unknown"),
        "status",
        Objects.toString(timer.getId().getTag("status"), "unknown"),
        "count",
        timer.count(),
        "totalMs",
        timer.totalTime(TimeUnit.MILLISECONDS),
        "maxMs",
        timer.max(TimeUnit.MILLISECONDS));
  }

  @Override
  @Transactional
  public void scan() {
    Instant now = clock.instant();
    for (DeviceEntity device :
        devices.selectList(
            new QueryWrapper<DeviceEntity>().le("last_seen_at", now.minusSeconds(offlineSeconds))))
      report(
          "DEVICE_OFFLINE", device.getId(), device.getId(), null, "设备心跳超时", "超过配置阈值未收到心跳，请检查模拟设备");
    for (DeviceEventEntity event :
        events.selectList(
            new QueryWrapper<DeviceEventEntity>()
                .eq("status", "WAITING")
                .le("created_at", now.minusSeconds(30))))
      report(
          "EVENT_GAP",
          event.getId(),
          event.getResourceId(),
          event.getBookingId(),
          "事件等待前序号",
          "等待 sequenceNo=" + event.getSequenceNo() + " 的前序事件；请补发缺失序号");
    for (UsageSessionEntity session :
        sessions.selectList(new QueryWrapper<UsageSessionEntity>().ne("status", "FINISHED"))) {
      BookingEntity booking = bookings.selectById(session.getBookingId());
      if (booking != null && booking.getEndAt().isBefore(now))
        report(
            "SESSION_OVERRUN",
            session.getId(),
            session.getResourceId(),
            session.getBookingId(),
            "会话超过预约结束时间",
            "请核实离场事件；系统不会自动猜测离场时间或电量");
    }
  }

  private IncidentView view(IncidentEntity incident) {
    return new IncidentView(
        incident.getId(),
        incident.getSourceType(),
        incident.getSourceId(),
        incident.getResourceId(),
        incident.getBookingId(),
        incident.getTitle(),
        incident.getDetail(),
        incident.getStatus(),
        incident.getResolution(),
        incident.getCreatedAt(),
        incident.getResolvedAt());
  }
}
