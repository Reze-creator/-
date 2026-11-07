CREATE TABLE app_user (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_user_username UNIQUE(username)
);
CREATE TABLE user_session (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_session_expiry ON user_session(expires_at);
CREATE TABLE parking_resource (
  id VARCHAR(36) PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  park_code VARCHAR(32) NOT NULL,
  park_name VARCHAR(60) NOT NULL,
  location VARCHAR(100) NOT NULL,
  resource_type VARCHAR(16) NOT NULL,
  power_kw INT NOT NULL DEFAULT 0,
  hourly_rate DECIMAL(10,2) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_resource_code UNIQUE(code)
);
CREATE INDEX idx_resource_park_type ON parking_resource(park_code, resource_type);
CREATE TABLE booking (
  id VARCHAR(36) PRIMARY KEY,
  booking_no VARCHAR(40) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  fingerprint VARCHAR(64) NOT NULL,
  resource_id VARCHAR(36) NOT NULL,
  resource_code VARCHAR(32) NOT NULL,
  park_name VARCHAR(60) NOT NULL,
  resource_type VARCHAR(16) NOT NULL,
  plate_number VARCHAR(16) NOT NULL,
  start_at TIMESTAMP(6) NOT NULL,
  end_at TIMESTAMP(6) NOT NULL,
  hourly_rate DECIMAL(10,2) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(24) NOT NULL,
  hold_until TIMESTAMP(6) NOT NULL,
  paid_at TIMESTAMP(6),
  cancelled_at TIMESTAMP(6),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_booking_no UNIQUE(booking_no),
  CONSTRAINT uk_booking_request UNIQUE(user_id,request_id),
  CONSTRAINT fk_booking_user FOREIGN KEY(user_id) REFERENCES app_user(id),
  CONSTRAINT fk_booking_resource FOREIGN KEY(resource_id) REFERENCES parking_resource(id)
);
CREATE INDEX idx_booking_resource_time ON booking(resource_id,start_at,end_at,status);
CREATE INDEX idx_booking_user_created ON booking(user_id,created_at,id);
CREATE INDEX idx_booking_expiry ON booking(status,hold_until);
CREATE TABLE mock_payment (
  id VARCHAR(36) PRIMARY KEY,
  booking_id VARCHAR(36) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  paid_at TIMESTAMP(6) NOT NULL,
  refunded_at TIMESTAMP(6),
  CONSTRAINT uk_payment_booking UNIQUE(booking_id),
  CONSTRAINT fk_payment_booking FOREIGN KEY(booking_id) REFERENCES booking(id)
);
