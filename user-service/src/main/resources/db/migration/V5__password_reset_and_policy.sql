DROP INDEX IF EXISTS users.idx_users_password_rollback_token;

ALTER TABLE users.users
    DROP COLUMN IF EXISTS password_rollback_token,
    DROP COLUMN IF EXISTS password_rollback_expires_at,
    ADD COLUMN  password_reset_jti      VARCHAR(64),
    ADD COLUMN  strict_password_policy  BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX idx_users_password_reset_jti
    ON users.users (password_reset_jti)
    WHERE password_reset_jti IS NOT NULL;
