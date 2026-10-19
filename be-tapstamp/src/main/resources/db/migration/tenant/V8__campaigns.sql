-- Kampaniya SMS-ləri (qərar 49): sahib yaradır, platforma admini (ödənişdən sonra) təsdiqləyir, fon işi göndərir.

-- Müştəri kampaniya SMS-lərindən imtina edə bilər (Parametrlər); NULL — alır
ALTER TABLE customers ADD COLUMN marketing_opt_out_at timestamptz;

CREATE TABLE campaigns (
    id                   uuid PRIMARY KEY,
    text                 text        NOT NULL CHECK (length(text) BETWEEN 1 AND 1000),
    audience             text        NOT NULL CHECK (audience IN ('ALL', 'INACTIVE', 'NEAR_REWARD', 'UNUSED_GIFT_CARD')),
    inactive_days        integer CHECK (inactive_days IS NULL OR inactive_days BETWEEN 7 AND 365),
    branch_id            uuid REFERENCES branches (id),
    recipients           integer     NOT NULL CHECK (recipients >= 0),
    segments_per_message integer     NOT NULL CHECK (segments_per_message > 0),
    total_sms            integer     NOT NULL CHECK (total_sms >= 0),
    status               text        NOT NULL CHECK (status IN
                             ('PENDING_APPROVAL', 'APPROVED', 'SENDING', 'SENT', 'REJECTED', 'CANCELLED')),
    reject_reason        text,
    created_by           uuid        NOT NULL REFERENCES staff_users (id),
    created_at           timestamptz NOT NULL,
    decided_at           timestamptz,
    decided_by           uuid,       -- platforma admini (platforma bazasında)
    sent_count           integer     NOT NULL DEFAULT 0,
    failed_count         integer     NOT NULL DEFAULT 0,
    finished_at          timestamptz
);
CREATE INDEX campaigns_status_idx ON campaigns (status, created_at);

-- Alıcılar kampaniya yaradılanda müəyyənləşir (təsdiq üçün hesablanan say dəyişmir)
CREATE TABLE campaign_recipients (
    campaign_id uuid        NOT NULL REFERENCES campaigns (id),
    customer_id uuid        NOT NULL REFERENCES customers (id),
    -- SENDING: göndərişə götürülüb (eyni anda işləyən iki backend eyni SMS-i iki dəfə göndərməsin)
    status      text        NOT NULL CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    claimed_at  timestamptz,
    sent_at     timestamptz,
    error       text,
    PRIMARY KEY (campaign_id, customer_id)
);
CREATE INDEX campaign_recipients_pending_idx ON campaign_recipients (campaign_id) WHERE status = 'PENDING';
