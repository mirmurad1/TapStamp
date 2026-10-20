-- Avtomatik xatırlatmalar (qərar #55): yalnız web push ilə (pulsuz), default söndürülü.

CREATE TABLE reminder_settings (
    id                  smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    near_reward_enabled boolean NOT NULL DEFAULT false,
    winback_enabled     boolean NOT NULL DEFAULT false,
    winback_days        int     NOT NULL DEFAULT 30 CHECK (winback_days IN (30, 60, 90)),
    unused_gift_enabled boolean NOT NULL DEFAULT false,
    updated_at          timestamptz,
    updated_by          uuid
);
INSERT INTO reminder_settings (id) VALUES (1);

-- Göndərilən xatırlatmalar: hər növ bir "dövrdə" (son gəlişdən sonra; hədiyyə üzrə — hər hədiyyəyə) bir dəfə
CREATE TABLE reminder_log (
    id          uuid PRIMARY KEY,
    customer_id uuid        NOT NULL REFERENCES customers (id),
    kind        text        NOT NULL CHECK (kind IN ('NEAR_REWARD', 'WINBACK', 'UNUSED_GIFT')),
    cycle_key   text        NOT NULL CHECK (length(cycle_key) <= 100),
    sent_at     timestamptz NOT NULL,
    UNIQUE (customer_id, kind, cycle_key)
);
CREATE INDEX reminder_log_customer_sent_idx ON reminder_log (customer_id, sent_at);
CREATE INDEX reminder_log_sent_idx ON reminder_log (sent_at);
