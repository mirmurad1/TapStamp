-- Bonus kampaniyasının başlanğıc bildirişi: kimə göndərildi və çatdırıldımı (qərar #59).
-- PENDING — növbədədir; DELIVERED — push xidməti (Google, Apple) qəbul edib; FAILED — cihaz yoxdur və ya alınmadı.

CREATE TABLE bonus_rule_deliveries (
    rule_id     uuid        NOT NULL REFERENCES bonus_rules (id) ON DELETE CASCADE,
    customer_id uuid        NOT NULL REFERENCES customers (id),
    status      text        NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    PRIMARY KEY (rule_id, customer_id)
);
CREATE INDEX bonus_rule_deliveries_rule_status_idx ON bonus_rule_deliveries (rule_id, status);
