package com.parking.service;

import com.parking.common.Pages;
import com.parking.vo.AuditView;
import com.parking.vo.IncidentView;
import com.parking.vo.UserView;
import java.util.Map;

public interface OperationsService {
  void report(
      String type,
      String sourceId,
      String resourceId,
      String bookingId,
      String title,
      String detail);

  void audit(UserView actor, String action, String targetId, String detail);

  Pages<IncidentView> incidents(String status, int page, int size);

  IncidentView resolve(UserView actor, String id, String resolution);

  Pages<AuditView> audits(int page, int size);

  Map<String, Object> overview();

  void scan();
}
