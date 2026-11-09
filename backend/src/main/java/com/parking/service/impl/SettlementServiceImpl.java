package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BillingRules;
import com.parking.entity.BookingEntity;
import com.parking.entity.PaymentEntity;
import com.parking.entity.UsageBillEntity;
import com.parking.entity.UsageSessionEntity;
import com.parking.mapper.PaymentMapper;
import com.parking.mapper.UsageBillMapper;
import com.parking.service.OutboxService;
import com.parking.service.SettlementService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 计算并保存最终账单。模拟补扣和退款不访问外部资金渠道。 */
@Service
public class SettlementServiceImpl implements SettlementService {
  private final UsageBillMapper bills;
  private final PaymentMapper payments;
  private final Clock clock;
  private final OutboxService outbox;

  public SettlementServiceImpl(
      UsageBillMapper bills, PaymentMapper payments, Clock clock, OutboxService outbox) {
    this.bills = bills;
    this.payments = payments;
    this.clock = clock;
    this.outbox = outbox;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void settle(BookingEntity booking, UsageSessionEntity session) {
    if (bills.selectCount(new QueryWrapper<UsageBillEntity>().eq("booking_id", booking.getId()))
        > 0) return;
    PaymentEntity payment =
        payments.selectOne(new QueryWrapper<PaymentEntity>().eq("booking_id", booking.getId()));
    if (payment == null || !"SUCCESS".equals(payment.getStatus()))
      throw new IllegalStateException("Usage settlement requires a successful reservation payment");
    long minutes = BillingRules.parkingMinutes(session.getEnteredAt(), session.getExitedAt());
    BigDecimal parking = BillingRules.parkingAmount(booking.getHourlyRate(), minutes);
    BigDecimal energy = BillingRules.energyAmount(session.getEnergyKwh(), session.getEnergyRate());
    BigDecimal total = parking.add(energy);
    BigDecimal delta = total.subtract(payment.getAmount());
    UsageBillEntity bill = new UsageBillEntity();
    bill.setId(UUID.randomUUID().toString());
    bill.setBookingId(booking.getId());
    bill.setSessionId(session.getId());
    bill.setUserId(booking.getUserId());
    bill.setParkingMinutes(minutes);
    bill.setParkingAmount(parking);
    bill.setEnergyKwh(session.getEnergyKwh());
    bill.setEnergyRate(session.getEnergyRate());
    bill.setEnergyAmount(energy);
    bill.setTotalAmount(total);
    bill.setPrepaidAmount(payment.getAmount());
    bill.setAdditionalAmount(delta.max(BigDecimal.ZERO));
    bill.setRefundAmount(delta.negate().max(BigDecimal.ZERO));
    bill.setStatus("SIMULATED_SETTLED");
    bill.setCreatedAt(clock.instant());
    bills.insert(bill);
    // 预付款已计入最终账单；不把差额退款误标为预约取消时的全额退款。
    payment.setStatus("SETTLED");
    payments.updateById(payment);
    outbox.enqueue("BILL_SETTLED", bill.getId());
  }
}
