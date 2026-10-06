-- Platforma bazası: yalnız tenant reyestri, domenlər və platforma səviyyəsində məlumatlar.
-- Şirkətlərin müştəri məlumatları burada SAXLANILMIR — onlar hər şirkətin öz bazasındadır.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE tenants (
    id                    uuid PRIMARY KEY,
    slug                  citext      NOT NULL UNIQUE CHECK (slug ~ '^[a-z][a-z0-9_]{1,30}$'),
    display_name          jsonb       NOT NULL CHECK (display_name ? 'az'),
    status                text        NOT NULL CHECK (status IN
                              ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'MIGRATION_FAILED', 'DECOMMISSIONED')),
    default_language      text        NOT NULL DEFAULT 'az' CHECK (default_language IN ('az', 'en', 'ru')),
    timezone              text        NOT NULL DEFAULT 'Asia/Baku',
    db_host               text        NOT NULL,
    db_port               integer     NOT NULL CHECK (db_port BETWEEN 1 AND 65535),
    db_name               text        NOT NULL UNIQUE,
    db_owner_username     text        NOT NULL UNIQUE,
    db_owner_password_enc bytea       NOT NULL,
    db_app_username       text        NOT NULL UNIQUE,
    db_app_password_enc   bytea       NOT NULL,
    schema_version        text,
    last_migration_error  text,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0
);

CREATE TABLE tenant_domains (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    hostname   citext      NOT NULL UNIQUE CHECK (hostname ~ '^[a-z0-9-]+(\.[a-z0-9-]+)+$'),
    is_primary boolean     NOT NULL DEFAULT false,
    tls_status text        NOT NULL DEFAULT 'PENDING' CHECK (tls_status IN ('PENDING', 'ISSUED', 'FAILED')),
    created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX tenant_domains_one_primary ON tenant_domains (tenant_id) WHERE is_primary;
CREATE INDEX tenant_domains_tenant_idx ON tenant_domains (tenant_id);

-- Platforma səviyyəsindəki məlumat açarları (DEK); KEK ilə şifrələnib
CREATE TABLE data_encryption_keys (
    id          uuid PRIMARY KEY,
    purpose     text        NOT NULL CHECK (purpose IN ('PII', 'NFC_KEYS', 'TOTP', 'TENANT_DB_CREDENTIALS')),
    wrapped_key bytea       NOT NULL,
    kek_id      text        NOT NULL,
    status      text        NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at  timestamptz NOT NULL
);
CREATE UNIQUE INDEX data_encryption_keys_one_active ON data_encryption_keys (purpose) WHERE status = 'ACTIVE';

-- Platforma audit jurnalı (yalnız əlavə olunur)
CREATE TABLE platform_audit_logs (
    id          uuid PRIMARY KEY,
    actor_id    uuid,
    action      text        NOT NULL,
    target_type text,
    target_id   text,
    ip          inet,
    user_agent  text,
    details     jsonb,
    created_at  timestamptz NOT NULL
);
CREATE INDEX platform_audit_logs_created_idx ON platform_audit_logs (created_at DESC);

-- Tətbiq rolu miqrasiya tarixçəsini dəyişə bilməz; audit jurnalı dəyişilməzdir
REVOKE ALL ON flyway_schema_history FROM "${appRole}";
REVOKE UPDATE, DELETE, TRUNCATE ON platform_audit_logs FROM "${appRole}";
