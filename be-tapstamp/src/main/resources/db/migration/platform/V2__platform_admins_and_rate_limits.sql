-- Platforma adminləri (SUPER_ADMIN): şirkət işçiləri ilə eyni autentifikasiya modeli, ayrıca bazada.

CREATE TABLE platform_admins (
    id                  uuid PRIMARY KEY,
    email               citext      NOT NULL UNIQUE,
    password_hash       text,                       -- Argon2id (PHC formatı)
    display_name        text CHECK (display_name IS NULL OR length(display_name) <= 80),
    preferred_language  text        NOT NULL DEFAULT 'az' CHECK (preferred_language IN ('az', 'en', 'ru')),
    status              text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'INVITED')),
    totp_secret_enc     bytea,
    totp_enabled_at     timestamptz,
    totp_last_used_step bigint,
    failed_login_count  integer     NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    password_changed_at timestamptz,
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0
);

CREATE TABLE platform_admin_sessions (
    id                  uuid PRIMARY KEY,
    user_id             uuid        NOT NULL REFERENCES platform_admins (id) ON DELETE CASCADE,
    token_hash          bytea       NOT NULL UNIQUE,
    state               text        NOT NULL CHECK (state IN ('PENDING_MFA', 'MFA_ENROLLMENT', 'ACTIVE')),
    created_at          timestamptz NOT NULL,
    last_activity_at    timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    mfa_verified_at     timestamptz,
    step_up_at          timestamptz,
    mfa_failed_attempts integer     NOT NULL DEFAULT 0,
    ip                  inet,
    user_agent          text,
    revoked_at          timestamptz,
    revoke_reason       text
);
CREATE INDEX platform_admin_sessions_user_idx ON platform_admin_sessions (user_id) WHERE revoked_at IS NULL;

CREATE TABLE platform_recovery_codes (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES platform_admins (id) ON DELETE CASCADE,
    code_hash  bytea       NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX platform_recovery_codes_user_idx ON platform_recovery_codes (user_id);

CREATE TABLE platform_password_reset_tokens (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES platform_admins (id) ON DELETE CASCADE,
    token_hash   bytea       NOT NULL UNIQUE,
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    requested_ip inet,
    created_at   timestamptz NOT NULL
);

-- Rate limiting (Bucket4j, SELECT ... FOR UPDATE rejimi): açarlarda şəxsi məlumat yoxdur (HMAC).
-- state ilk sorğuda boş sətir kimi yaradılır, sonra doldurulur; expires_at — millisaniyə (Bucket4j formatı)
CREATE TABLE rate_limit_buckets (
    id         text PRIMARY KEY,
    state      bytea,
    expires_at bigint
);
CREATE INDEX rate_limit_buckets_expires_idx ON rate_limit_buckets (expires_at);
