-- Tenant bazasının ilkin sxemi. Bu baza yalnız bir şirkətə məxsusdur, ona görə company_id sütunu yoxdur.
-- Placeholder-lər: ${ownerRole} — sxem sahibi (miqrasiyalar), ${appRole} — tətbiqin rolu (yalnız DML).

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =====================================================================
-- Şirkət və filiallar
-- =====================================================================

-- Şirkət profili: həmişə tək sətir
CREATE TABLE company_profile (
    singleton             boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    name                  jsonb       NOT NULL CHECK (name ? 'az'),
    description           jsonb CHECK (description IS NULL OR description ? 'az'),
    timezone              text        NOT NULL DEFAULT 'Asia/Baku',
    default_language      text        NOT NULL DEFAULT 'az' CHECK (default_language IN ('az', 'en', 'ru')),
    allowed_phone_regions text[]      NOT NULL DEFAULT '{AZ}',
    sms_sender_name       text CHECK (sms_sender_name IS NULL OR sms_sender_name ~ '^[A-Za-z0-9 ]{1,11}$'),
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0
);

CREATE TABLE branches (
    id             uuid PRIMARY KEY,
    name           jsonb       NOT NULL CHECK (name ? 'az'),
    address        jsonb CHECK (address IS NULL OR address ? 'az'),
    description    jsonb CHECK (description IS NULL OR description ? 'az'),
    -- {"mon": [["08:00","22:00"]], ...}; istəyə bağlı iş saatları qaydası üçün
    business_hours jsonb,
    status         text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    version        bigint      NOT NULL DEFAULT 0
);

-- =====================================================================
-- İşçilər (COMPANY_OWNER, BRANCH_MANAGER, STAFF)
-- =====================================================================

CREATE TABLE staff_users (
    id                  uuid PRIMARY KEY,
    email               citext      NOT NULL UNIQUE,
    password_hash       text,                       -- dəvət qəbul olunana qədər NULL; Argon2id (PHC formatı)
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

CREATE TABLE staff_roles (
    id            uuid PRIMARY KEY,
    staff_user_id uuid        NOT NULL REFERENCES staff_users (id) ON DELETE CASCADE,
    role          text        NOT NULL CHECK (role IN ('COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF')),
    branch_id     uuid REFERENCES branches (id),
    created_at    timestamptz NOT NULL,
    CHECK ((role = 'COMPANY_OWNER') = (branch_id IS NULL)),
    UNIQUE NULLS NOT DISTINCT (staff_user_id, role, branch_id)
);

CREATE TABLE staff_sessions (
    id                  uuid PRIMARY KEY,
    staff_user_id       uuid        NOT NULL REFERENCES staff_users (id) ON DELETE CASCADE,
    token_hash          bytea       NOT NULL UNIQUE,
    state               text        NOT NULL CHECK (state IN ('PENDING_MFA', 'MFA_ENROLLMENT', 'ACTIVE')),
    created_at          timestamptz NOT NULL,
    last_activity_at    timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    mfa_verified_at     timestamptz,
    step_up_at          timestamptz,
    ip                  inet,
    user_agent          text,
    revoked_at          timestamptz,
    revoke_reason       text
);
CREATE INDEX staff_sessions_user_active_idx ON staff_sessions (staff_user_id) WHERE revoked_at IS NULL;

CREATE TABLE staff_recovery_codes (
    id            uuid PRIMARY KEY,
    staff_user_id uuid        NOT NULL REFERENCES staff_users (id) ON DELETE CASCADE,
    code_hash     bytea       NOT NULL,
    used_at       timestamptz,
    created_at    timestamptz NOT NULL
);
CREATE INDEX staff_recovery_codes_user_idx ON staff_recovery_codes (staff_user_id);

CREATE TABLE staff_invitations (
    id          uuid PRIMARY KEY,
    email       citext      NOT NULL,
    role        text        NOT NULL CHECK (role IN ('COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF')),
    branch_id   uuid REFERENCES branches (id),
    token_hash  bytea       NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at  timestamptz,
    invited_by  uuid REFERENCES staff_users (id),
    created_at  timestamptz NOT NULL,
    CHECK ((role = 'COMPANY_OWNER') = (branch_id IS NULL))
);

CREATE TABLE password_reset_tokens (
    id            uuid PRIMARY KEY,
    staff_user_id uuid        NOT NULL REFERENCES staff_users (id) ON DELETE CASCADE,
    token_hash    bytea       NOT NULL UNIQUE,
    expires_at    timestamptz NOT NULL,
    used_at       timestamptz,
    requested_ip  inet,
    created_at    timestamptz NOT NULL
);

-- =====================================================================
-- Müştərilər
-- =====================================================================

CREATE TABLE customers (
    id                 uuid PRIMARY KEY,
    phone_enc          bytea,                 -- AES-256-GCM (envelope encryption); silinəndə NULL
    phone_hash         bytea,                 -- HMAC-SHA256 (tenant açarı); axtarış və unikallıq üçün
    phone_masked       text,                  -- "+994 50 ***-**-67"
    name               text CHECK (name IS NULL OR length(name) <= 80),
    preferred_language text        NOT NULL CHECK (preferred_language IN ('az', 'en', 'ru')),
    status             text        NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
    registered_at      timestamptz NOT NULL,
    last_visit_at      timestamptz,
    last_branch_id     uuid REFERENCES branches (id),
    deleted_at         timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    -- Silinmiş müştəridə şəxsi məlumat qalmır
    CHECK ((status = 'DELETED') = (phone_hash IS NULL)),
    CHECK (status <> 'DELETED' OR (phone_enc IS NULL AND phone_masked IS NULL AND name IS NULL))
);
CREATE UNIQUE INDEX customers_phone_hash_uq ON customers (phone_hash) WHERE phone_hash IS NOT NULL;
CREATE INDEX customers_last_visit_idx ON customers (last_visit_at DESC NULLS LAST);
CREATE INDEX customers_registered_idx ON customers (registered_at DESC);
CREATE INDEX customers_language_idx ON customers (preferred_language);
CREATE INDEX customers_name_trgm_idx ON customers USING gin (name gin_trgm_ops);

CREATE TABLE customer_sessions (
    id                 uuid PRIMARY KEY,
    customer_id        uuid        NOT NULL REFERENCES customers (id),
    access_token_hash  bytea       NOT NULL UNIQUE,
    access_expires_at  timestamptz NOT NULL,
    refresh_expires_at timestamptz NOT NULL,
    device_label       text,
    user_agent         text,
    created_ip         inet,
    last_ip            inet,
    created_at         timestamptz NOT NULL,
    last_seen_at       timestamptz NOT NULL,
    revoked_at         timestamptz,
    revoke_reason      text
);
CREATE INDEX customer_sessions_customer_idx ON customer_sessions (customer_id) WHERE revoked_at IS NULL;

-- Refresh token-lərin rotasiya tarixçəsi: istifadə olunmuş token yenidən gələrsə — təkrar istifadə aşkarlanır
CREATE TABLE customer_refresh_tokens (
    id          uuid PRIMARY KEY,
    session_id  uuid        NOT NULL REFERENCES customer_sessions (id) ON DELETE CASCADE,
    token_hash  bytea       NOT NULL UNIQUE,
    issued_at   timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE INDEX customer_refresh_tokens_session_idx ON customer_refresh_tokens (session_id);

-- =====================================================================
-- NFC çipləri və toxunuşlar
-- =====================================================================

-- Tenant səviyyəsində məlumat açarları (DEK); KEK ilə şifrələnib
CREATE TABLE data_encryption_keys (
    id          uuid PRIMARY KEY,
    purpose     text        NOT NULL CHECK (purpose IN ('PII', 'NFC_KEYS', 'TOTP')),
    wrapped_key bytea       NOT NULL,
    kek_id      text        NOT NULL,
    status      text        NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at  timestamptz NOT NULL
);
CREATE UNIQUE INDEX data_encryption_keys_one_active ON data_encryption_keys (purpose) WHERE status = 'ACTIVE';

CREATE TABLE nfc_tags (
    id           uuid PRIMARY KEY,
    branch_id    uuid        NOT NULL REFERENCES branches (id),
    uid          bytea       NOT NULL UNIQUE CHECK (length(uid) = 7),
    type         text        NOT NULL CHECK (type IN ('NTAG424_SUN', 'SIMULATED')),
    label        text        NOT NULL CHECK (length(label) BETWEEN 1 AND 60),
    status       text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- Son qəbul olunmuş oxunma sayğacı (SDMReadCtr, 24 bit); yeni toxunuşun sayğacı bundan böyük olmalıdır
    last_counter integer     NOT NULL DEFAULT -1 CHECK (last_counter BETWEEN -1 AND 16777215),
    last_tap_at  timestamptz,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    disabled_at  timestamptz,
    disabled_by  uuid REFERENCES staff_users (id),
    version      bigint      NOT NULL DEFAULT 0
);
CREATE INDEX nfc_tags_branch_idx ON nfc_tags (branch_id);

CREATE TABLE nfc_tag_keys (
    id                       uuid PRIMARY KEY,
    tag_id                   uuid        NOT NULL REFERENCES nfc_tags (id) ON DELETE CASCADE,
    key_version              integer     NOT NULL,
    status                   text        NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'RETIRED')),
    app_master_key_enc       bytea       NOT NULL,  -- K0
    meta_read_key_enc        bytea       NOT NULL,  -- K1 (SDMMetaReadKey)
    file_read_key_enc        bytea       NOT NULL,  -- K2 (SDMFileReadKey)
    provisioning_exported_at timestamptz,
    activated_at             timestamptz,
    retired_at               timestamptz,
    created_by               uuid REFERENCES staff_users (id),
    created_at               timestamptz NOT NULL,
    UNIQUE (tag_id, key_version)
);
CREATE UNIQUE INDEX nfc_tag_keys_one_active ON nfc_tag_keys (tag_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX nfc_tag_keys_one_pending ON nfc_tag_keys (tag_id) WHERE status = 'PENDING';

-- Hər cəhd (uğurlu və rədd edilən) — yalnız əlavə olunur
CREATE TABLE tap_events (
    id              uuid PRIMARY KEY,
    branch_id       uuid REFERENCES branches (id),
    tag_id          uuid REFERENCES nfc_tags (id),
    stage           text        NOT NULL CHECK (stage IN ('VERIFY', 'CLAIM')),
    outcome         text        NOT NULL CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    reason          text,
    counter         integer,
    counter_gap     integer,
    customer_id     uuid REFERENCES customers (id),
    tap_token_id    uuid,
    stamp_id        uuid,
    ip              inet,
    browser_id_hash bytea,
    user_agent_hash bytea,
    created_at      timestamptz NOT NULL,
    CHECK (outcome = 'ACCEPTED' OR reason IS NOT NULL)
);
-- Replay qorunmasının ikinci qatı: eyni çipin eyni sayğacı iki dəfə qəbul oluna bilməz
CREATE UNIQUE INDEX tap_events_counter_uq ON tap_events (tag_id, counter)
    WHERE stage = 'VERIFY' AND outcome = 'ACCEPTED';
CREATE INDEX tap_events_created_idx ON tap_events (created_at DESC);
CREATE INDEX tap_events_tag_idx ON tap_events (tag_id, created_at DESC);
CREATE INDEX tap_events_customer_idx ON tap_events (customer_id, created_at DESC);

CREATE TABLE tap_tokens (
    id                      uuid PRIMARY KEY,
    token_hash              bytea       NOT NULL UNIQUE,
    branch_id               uuid        NOT NULL REFERENCES branches (id),
    tag_id                  uuid        NOT NULL REFERENCES nfc_tags (id),
    tap_event_id            uuid        NOT NULL REFERENCES tap_events (id),
    binding_hash            bytea       NOT NULL,
    status                  text        NOT NULL CHECK (status IN ('ACTIVE', 'RESERVED', 'CONSUMED', 'REJECTED', 'EXPIRED')),
    expires_at              timestamptz NOT NULL,
    reserved_challenge_id   uuid,
    reserved_until          timestamptz,
    consumed_at             timestamptz,
    consumed_by_customer_id uuid REFERENCES customers (id),
    created_at              timestamptz NOT NULL
);
CREATE INDEX tap_tokens_expiry_idx ON tap_tokens (expires_at) WHERE status IN ('ACTIVE', 'RESERVED');

CREATE TABLE otp_challenges (
    id                      uuid PRIMARY KEY,
    phone_hash              bytea       NOT NULL,
    phone_enc               bytea       NOT NULL,
    code_hash               bytea       NOT NULL,
    language                text        NOT NULL CHECK (language IN ('az', 'en', 'ru')),
    attempts                integer     NOT NULL DEFAULT 0,
    max_attempts            integer     NOT NULL DEFAULT 5,
    status                  text        NOT NULL CHECK (status IN ('ACTIVE', 'VERIFIED', 'EXPIRED', 'LOCKED', 'SUPERSEDED')),
    tap_token_id            uuid REFERENCES tap_tokens (id),
    registration_token_hash bytea UNIQUE,
    registration_expires_at timestamptz,
    ip                      inet,
    created_at              timestamptz NOT NULL,
    expires_at              timestamptz NOT NULL,
    verified_at             timestamptz,
    CHECK (attempts <= max_attempts)
);
CREATE INDEX otp_challenges_phone_idx ON otp_challenges (phone_hash, created_at DESC);

-- =====================================================================
-- Hüquqi sənədlər və razılıqlar
-- =====================================================================

CREATE TABLE legal_documents (
    id           uuid PRIMARY KEY,
    type         text        NOT NULL CHECK (type IN ('PRIVACY_POLICY', 'TERMS')),
    doc_version  integer     NOT NULL CHECK (doc_version > 0),
    content      jsonb       NOT NULL CHECK (content ? 'az'),
    published_at timestamptz NOT NULL,
    is_current   boolean     NOT NULL DEFAULT false,
    UNIQUE (type, doc_version)
);
CREATE UNIQUE INDEX legal_documents_one_current ON legal_documents (type) WHERE is_current;

CREATE TABLE consents (
    id               uuid PRIMARY KEY,
    customer_id      uuid        NOT NULL REFERENCES customers (id),
    document_id      uuid        NOT NULL REFERENCES legal_documents (id),
    document_type    text        NOT NULL,
    document_version integer     NOT NULL,
    language         text        NOT NULL CHECK (language IN ('az', 'en', 'ru')),
    accepted_at      timestamptz NOT NULL,
    ip               inet,
    user_agent       text,
    withdrawn_at     timestamptz
);
CREATE INDEX consents_customer_idx ON consents (customer_id);

-- =====================================================================
-- Loyallıq: qaydalar, hesablar, möhürlər, mükafatlar
-- =====================================================================

-- Versiyalı mükafat qaydası (məs. 9 möhür → 10-cu qəhvə pulsuz); yalnız bir aktiv versiya
CREATE TABLE reward_rules (
    id                 uuid PRIMARY KEY,
    rule_version       integer     NOT NULL UNIQUE CHECK (rule_version > 0),
    stamps_required    integer     NOT NULL CHECK (stamps_required BETWEEN 1 AND 50),
    reward_name        jsonb       NOT NULL CHECK (reward_name ? 'az'),
    reward_description jsonb CHECK (reward_description IS NULL OR reward_description ? 'az'),
    active             boolean     NOT NULL,
    created_by         uuid REFERENCES staff_users (id),
    created_at         timestamptz NOT NULL
);
CREATE UNIQUE INDEX reward_rules_one_active ON reward_rules (active) WHERE active;

-- Sui-istifadə qaydaları: branch_id NULL — şirkət üzrə standart, doluysa filial üzrə override
CREATE TABLE stamp_policies (
    id                          uuid PRIMARY KEY,
    branch_id                   uuid REFERENCES branches (id),
    min_interval_minutes        integer     NOT NULL DEFAULT 20 CHECK (min_interval_minutes BETWEEN 0 AND 1440),
    max_stamps_per_day          integer     NOT NULL DEFAULT 3 CHECK (max_stamps_per_day BETWEEN 1 AND 50),
    require_staff_approval      boolean     NOT NULL DEFAULT false,
    pending_timeout_minutes     integer     NOT NULL DEFAULT 30 CHECK (pending_timeout_minutes BETWEEN 1 AND 1440),
    enforce_business_hours      boolean     NOT NULL DEFAULT false,
    max_taps_per_tag_per_minute integer     NOT NULL DEFAULT 10 CHECK (max_taps_per_tag_per_minute BETWEEN 1 AND 600),
    updated_by                  uuid REFERENCES staff_users (id),
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    UNIQUE NULLS NOT DISTINCT (branch_id)
);

CREATE TABLE loyalty_accounts (
    id               uuid PRIMARY KEY,
    customer_id      uuid        NOT NULL UNIQUE REFERENCES customers (id),
    current_stamps   integer     NOT NULL DEFAULT 0 CHECK (current_stamps >= 0),
    total_stamps     integer     NOT NULL DEFAULT 0 CHECK (total_stamps >= 0),
    rewards_earned   integer     NOT NULL DEFAULT 0 CHECK (rewards_earned >= 0),
    rewards_redeemed integer     NOT NULL DEFAULT 0 CHECK (rewards_redeemed >= 0 AND rewards_redeemed <= rewards_earned),
    last_stamp_at    timestamptz,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0
);

CREATE TABLE rewards (
    id                       uuid PRIMARY KEY,
    customer_id              uuid        NOT NULL REFERENCES customers (id),
    rule_id                  uuid        NOT NULL REFERENCES reward_rules (id),
    stamps_required_snapshot integer     NOT NULL,
    reward_name_snapshot     jsonb       NOT NULL,
    status                   text        NOT NULL CHECK (status IN ('AVAILABLE', 'REDEEMED', 'REVOKED')),
    earned_at                timestamptz NOT NULL,
    redeemed_at              timestamptz,
    created_at               timestamptz NOT NULL,
    CHECK ((status = 'REDEEMED') = (redeemed_at IS NOT NULL))
);
CREATE INDEX rewards_customer_idx ON rewards (customer_id, status);
CREATE INDEX rewards_earned_idx ON rewards (earned_at);
CREATE INDEX rewards_redeemed_idx ON rewards (redeemed_at) WHERE redeemed_at IS NOT NULL;

CREATE TABLE stamps (
    id                  uuid PRIMARY KEY,
    branch_id           uuid        NOT NULL REFERENCES branches (id),
    customer_id         uuid        NOT NULL REFERENCES customers (id),
    tag_id              uuid REFERENCES nfc_tags (id),
    tap_token_id        uuid UNIQUE REFERENCES tap_tokens (id),   -- idempotentlik: bir token = ən çox bir möhür
    source              text        NOT NULL CHECK (source IN ('NFC_TAP', 'MANUAL')),
    status              text        NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'REVOKED')),
    local_date          date        NOT NULL,                     -- şirkətin saat qurşağında (gündəlik limit)
    reason              text,
    created_by_staff_id uuid REFERENCES staff_users (id),
    confirmed_at        timestamptz,
    confirmed_by        uuid REFERENCES staff_users (id),
    revoked_at          timestamptz,
    revoked_by          uuid REFERENCES staff_users (id),
    revoke_reason       text,
    reward_id           uuid REFERENCES rewards (id),
    created_at          timestamptz NOT NULL,
    CHECK (source <> 'MANUAL' OR (reason IS NOT NULL AND created_by_staff_id IS NOT NULL)),
    CHECK (status <> 'REVOKED' OR revoke_reason IS NOT NULL)
);
CREATE INDEX stamps_created_idx ON stamps (created_at);
CREATE INDEX stamps_branch_created_idx ON stamps (branch_id, created_at);
CREATE INDEX stamps_customer_created_idx ON stamps (customer_id, created_at DESC);
CREATE INDEX stamps_customer_day_idx ON stamps (customer_id, local_date) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX stamps_pending_idx ON stamps (branch_id, created_at) WHERE status = 'PENDING';

CREATE TABLE redemptions (
    id            uuid PRIMARY KEY,
    reward_id     uuid        NOT NULL REFERENCES rewards (id),
    customer_id   uuid        NOT NULL REFERENCES customers (id),
    code_hash     bytea       NOT NULL,
    qr_token_hash bytea       NOT NULL UNIQUE,
    status        text        NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    expires_at    timestamptz NOT NULL,
    branch_id     uuid REFERENCES branches (id),
    confirmed_by  uuid REFERENCES staff_users (id),
    confirmed_at  timestamptz,
    created_at    timestamptz NOT NULL,
    CHECK (status <> 'COMPLETED' OR (confirmed_at IS NOT NULL AND branch_id IS NOT NULL))
);
CREATE UNIQUE INDEX redemptions_pending_code_uq ON redemptions (code_hash) WHERE status = 'PENDING';
CREATE UNIQUE INDEX redemptions_pending_reward_uq ON redemptions (reward_id) WHERE status = 'PENDING';
CREATE INDEX redemptions_confirmed_idx ON redemptions (confirmed_at) WHERE status = 'COMPLETED';

-- =====================================================================
-- Audit, fırıldaq işarələri, idempotentlik
-- =====================================================================

-- Audit jurnalı: yalnız əlavə olunur (tətbiq rolunun UPDATE/DELETE hüququ yoxdur). Şəxsi məlumat yazılmır.
CREATE TABLE audit_logs (
    id          uuid PRIMARY KEY,
    actor_type  text        NOT NULL CHECK (actor_type IN ('STAFF', 'CUSTOMER', 'PLATFORM', 'SYSTEM')),
    actor_id    uuid,
    action      text        NOT NULL,
    target_type text,
    target_id   text,
    ip          inet,
    user_agent  text,
    details     jsonb,
    created_at  timestamptz NOT NULL
);
CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_id, created_at DESC);
CREATE INDEX audit_logs_action_idx ON audit_logs (action, created_at DESC);

CREATE TABLE fraud_flags (
    id              uuid PRIMARY KEY,
    customer_id     uuid REFERENCES customers (id),
    tag_id          uuid REFERENCES nfc_tags (id),
    type            text        NOT NULL CHECK (type IN
                        ('REPLAY_ATTEMPT', 'COUNTER_GAP', 'RAPID_MULTI_ACCOUNT', 'COOLDOWN_HAMMERING', 'BINDING_MISMATCH')),
    severity        text        NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    details         jsonb,
    created_at      timestamptz NOT NULL,
    resolved_at     timestamptz,
    resolved_by     uuid REFERENCES staff_users (id),
    resolution_note text
);
CREATE INDEX fraud_flags_customer_idx ON fraud_flags (customer_id, created_at DESC);
CREATE INDEX fraud_flags_open_idx ON fraud_flags (created_at DESC) WHERE resolved_at IS NULL;

CREATE TABLE idempotency_keys (
    scope           text        NOT NULL,
    key             text        NOT NULL,
    request_hash    bytea       NOT NULL,
    response_status integer     NOT NULL,
    response_body   jsonb,
    created_at      timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    PRIMARY KEY (scope, key)
);

-- =====================================================================
-- Saxlama müddəti və anonimləşdirmə (yalnız sahibin hüquqları ilə işləyən funksiyalar)
-- =====================================================================

-- Müştəri silinəndə onun toxunuş hadisələrindəki texniki izlər (IP, brauzer) silinir
CREATE FUNCTION anonymize_customer_tap_events(p_customer_id uuid) RETURNS integer
    LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp AS
$$
WITH updated AS (
    UPDATE tap_events SET ip = NULL, browser_id_hash = NULL, user_agent_hash = NULL
    WHERE customer_id = p_customer_id
    RETURNING 1)
SELECT count(*)::integer FROM updated
$$;

-- Saxlama müddəti bitmiş IP ünvanlarının silinməsi (fon işi çağırır)
CREATE FUNCTION purge_tap_event_ips(p_older_than timestamptz) RETURNS integer
    LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp AS
$$
WITH updated AS (
    UPDATE tap_events SET ip = NULL WHERE created_at < p_older_than AND ip IS NOT NULL
    RETURNING 1)
SELECT count(*)::integer FROM updated
$$;

-- =====================================================================
-- Hüquqlar: tətbiq rolu yalnız DML; audit və toxunuş jurnalı dəyişilməzdir
-- =====================================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${appRole}";
REVOKE ALL ON flyway_schema_history FROM "${appRole}";
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs, tap_events FROM "${appRole}";
ALTER DEFAULT PRIVILEGES FOR ROLE "${ownerRole}" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${appRole}";

REVOKE ALL ON FUNCTION anonymize_customer_tap_events(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION purge_tap_event_ips(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION anonymize_customer_tap_events(uuid) TO "${appRole}";
GRANT EXECUTE ON FUNCTION purge_tap_event_ips(timestamptz) TO "${appRole}";
