-- Ziyarətdən sonra rəy (qərar #61): NFC möhüründən 15 dəqiqə sonra müştəridən qiymət istənir.

CREATE TABLE feedback_settings (
    id             smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled        boolean NOT NULL DEFAULT false,
    -- Müştəridən ən çox neçə gündə bir soruşulur
    frequency_days int     NOT NULL DEFAULT 7 CHECK (frequency_days IN (1, 7, 14, 30)),
    updated_at     timestamptz,
    updated_by     uuid
);
INSERT INTO feedback_settings (id) VALUES (1);

-- Sorğu: ziyarətdən yaranır, due_at-da göndərilir (push), 3 gün cavab gözləyir
CREATE TABLE feedback_requests (
    id          uuid PRIMARY KEY,
    customer_id uuid        NOT NULL REFERENCES customers (id),
    branch_id   uuid        NOT NULL REFERENCES branches (id),
    visit_at    timestamptz NOT NULL,
    due_at      timestamptz NOT NULL,
    sent_at     timestamptz,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL
);
CREATE INDEX feedback_requests_due_idx ON feedback_requests (due_at) WHERE sent_at IS NULL;
CREATE INDEX feedback_requests_customer_idx ON feedback_requests (customer_id, created_at DESC);

-- Cavab: GOOD, OK, BAD; pis rəy admin panelində "həll olunmamış" kimi görünür
CREATE TABLE feedback (
    id              uuid PRIMARY KEY,
    request_id      uuid        NOT NULL UNIQUE REFERENCES feedback_requests (id),
    customer_id     uuid        NOT NULL REFERENCES customers (id),
    branch_id       uuid        NOT NULL REFERENCES branches (id),
    rating          text        NOT NULL CHECK (rating IN ('GOOD', 'OK', 'BAD')),
    reasons         text        CHECK (reasons IS NULL OR length(reasons) <= 200),
    comment         text        CHECK (comment IS NULL OR length(comment) <= 500),
    created_at      timestamptz NOT NULL,
    resolved_at     timestamptz,
    resolved_by     uuid,
    resolution_note text        CHECK (resolution_note IS NULL OR length(resolution_note) <= 500)
);
CREATE INDEX feedback_created_idx ON feedback (created_at DESC);
CREATE INDEX feedback_unresolved_bad_idx ON feedback (created_at) WHERE rating = 'BAD' AND resolved_at IS NULL;
