-- Push token storage for FCM (Android) and APNs (iOS)

CREATE TABLE push_tokens (
  user_id VARCHAR(128) NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  platform VARCHAR(16) NOT NULL,  -- 'fcm' or 'apns'
  token TEXT NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  PRIMARY KEY (user_id, device_id)
);

CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);
CREATE INDEX idx_push_tokens_platform ON push_tokens (platform);
