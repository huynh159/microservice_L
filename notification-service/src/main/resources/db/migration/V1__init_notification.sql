-- ==============================================================================
-- V1__init_notification.sql
-- Bảng chống xử lý trùng (Idempotency) và lịch sử thông báo
-- ==============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_log (
    id UUID PRIMARY KEY,
    event_id VARCHAR(64),
    recipient VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_log_event_id ON notification_log(event_id);
CREATE INDEX IF NOT EXISTS idx_notification_log_recipient ON notification_log(recipient);
