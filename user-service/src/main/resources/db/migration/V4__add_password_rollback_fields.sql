ALTER TABLE users.users
    ADD COLUMN previous_password_hash       VARCHAR(255),
    ADD COLUMN password_rollback_token      VARCHAR(64),
    ADD COLUMN password_rollback_expires_at TIMESTAMP;

CREATE UNIQUE INDEX idx_users_password_rollback_token
    ON users.users (password_rollback_token)
    WHERE password_rollback_token IS NOT NULL;
