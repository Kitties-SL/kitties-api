CREATE SCHEMA IF NOT EXISTS notification;

CREATE SEQUENCE notification.notifications_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE notification.notifications (
    id         BIGINT       PRIMARY KEY DEFAULT nextval('notification.notifications_seq'),
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    code       VARCHAR(100) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       TEXT,
    metadata   TEXT,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notifications_user_id ON notification.notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notification.notifications (user_id) WHERE read = FALSE;
