-- 预约预付款与实际履约账单分开保存，保留第一阶段历史数据。
ALTER TABLE booking ADD COLUMN last_event_sequence INT NOT NULL DEFAULT 0;

CREATE TABLE simulated_device (
  id VARCHAR(36) PRIMARY KEY,
  last_seen_at TIMESTAMP(6),
  fault BOOLEAN NOT NULL DEFAULT FALSE,
  fault_message VARCHAR(200),
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_device_resource FOREIGN KEY(id) REFERENCES parking_resource(id)
);

CREATE TABLE usage_session (
  id VARCHAR(36) PRIMARY KEY,
  booking_id VARCHAR(36) NOT NULL,
  resource_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL,
  entered_at TIMESTAMP(6) NOT NULL,
  charging_started_at TIMESTAMP(6),
  charging_stopped_at TIMESTAMP(6),
  exited_at TIMESTAMP(6),
  last_event_at TIMESTAMP(6) NOT NULL,
  energy_kwh DECIMAL(12,3) NOT NULL DEFAULT 0,
  energy_rate DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_session_booking UNIQUE(booking_id),
  CONSTRAINT fk_usage_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
  CONSTRAINT fk_usage_resource FOREIGN KEY(resource_id) REFERENCES parking_resource(id),
  CONSTRAINT fk_usage_user FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_usage_resource_status ON usage_session(resource_id,status);
CREATE INDEX idx_usage_user_created ON usage_session(user_id,created_at,id);

CREATE TABLE device_event (
  id VARCHAR(64) PRIMARY KEY,
  booking_id VARCHAR(36) NOT NULL,
  resource_id VARCHAR(36) NOT NULL,
  sequence_no INT NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  energy_kwh DECIMAL(12,3) NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(200),
  created_at TIMESTAMP(6) NOT NULL,
  processed_at TIMESTAMP(6),
  CONSTRAINT uk_event_sequence UNIQUE(booking_id,sequence_no),
  CONSTRAINT fk_event_booking FOREIGN KEY(booking_id) REFERENCES booking(id)
);
CREATE INDEX idx_event_pending ON device_event(status,created_at);

CREATE TABLE usage_bill (
  id VARCHAR(36) PRIMARY KEY,
  booking_id VARCHAR(36) NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  parking_minutes BIGINT NOT NULL,
  parking_amount DECIMAL(10,2) NOT NULL,
  energy_kwh DECIMAL(12,3) NOT NULL,
  energy_rate DECIMAL(10,2) NOT NULL,
  energy_amount DECIMAL(10,2) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  prepaid_amount DECIMAL(10,2) NOT NULL,
  additional_amount DECIMAL(10,2) NOT NULL,
  refund_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_bill_booking UNIQUE(booking_id),
  CONSTRAINT uk_bill_session UNIQUE(session_id),
  CONSTRAINT fk_bill_booking FOREIGN KEY(booking_id) REFERENCES booking(id),
  CONSTRAINT fk_bill_session FOREIGN KEY(session_id) REFERENCES usage_session(id)
);
CREATE INDEX idx_bill_user_created ON usage_bill(user_id,created_at,id);

CREATE TABLE operation_incident (
  id VARCHAR(36) PRIMARY KEY,
  source_type VARCHAR(24) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  resource_id VARCHAR(36),
  booking_id VARCHAR(36),
  title VARCHAR(100) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL,
  resolution VARCHAR(500),
  resolved_by VARCHAR(36),
  created_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6),
  CONSTRAINT uk_incident_source UNIQUE(source_type,source_id)
);
CREATE INDEX idx_incident_status ON operation_incident(status,created_at,id);

CREATE TABLE operation_audit (
  id VARCHAR(36) PRIMARY KEY,
  actor_id VARCHAR(36) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL
);
