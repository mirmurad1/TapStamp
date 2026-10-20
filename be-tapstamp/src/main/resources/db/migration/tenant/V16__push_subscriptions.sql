-- Web push (qərar #54): müştərinin cihazlarındakı brauzer abunələri. Ünvan (endpoint) push serverindədir (Google, Apple,
-- Mozilla, Microsoft); p256dh və auth — mesajın şifrələnməsi üçün brauzerin açarları (RFC 8291).
CREATE TABLE push_subscriptions (
    id            uuid PRIMARY KEY,
    customer_id   uuid        NOT NULL REFERENCES customers (id),
    endpoint      text        NOT NULL UNIQUE CHECK (length(endpoint) <= 1000),
    p256dh        text        NOT NULL CHECK (length(p256dh) <= 200),
    auth          text        NOT NULL CHECK (length(auth) <= 100),
    device_label  text        CHECK (device_label IS NULL OR length(device_label) <= 120),
    created_at    timestamptz NOT NULL,
    last_sent_at  timestamptz
);
CREATE INDEX push_subscriptions_customer_idx ON push_subscriptions (customer_id);
