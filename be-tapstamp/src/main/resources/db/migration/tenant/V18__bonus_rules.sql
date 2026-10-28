-- Bonus qaydaları (xoşbəxt saatlar və tarixli kampaniyalar): şərt ödənəndə NFC möhürünə əlavə bonus möhür verilir.
-- Bax: docs/architecture-plan.md §18, qərar #59.

CREATE TABLE bonus_rules (
    id             uuid PRIMARY KEY,
    name           jsonb       NOT NULL CHECK (name ? 'az'),
    -- Hər NFC möhürünə əlavə olunan bonus möhür (1 = "ikiqat möhür")
    bonus_stamps   int         NOT NULL CHECK (bonus_stamps BETWEEN 1 AND 5),
    -- Həftənin günləri bit maskası: bazar ertəsi = 1, ..., bazar = 64
    days_mask      int         NOT NULL CHECK (days_mask BETWEEN 1 AND 127),
    -- Saat aralığı (şirkətin saatı ilə); ikisi də boşdursa — bütün gün
    start_time     time,
    end_time       time,
    -- Tarix aralığı (daxil olmaqla); boşdursa — müddətsiz
    starts_on      date,
    ends_on        date,
    -- Boşdursa — bütün filiallar
    branch_id      uuid REFERENCES branches (id),
    active         boolean     NOT NULL DEFAULT true,
    -- Başlayanda bildirişi açıq müştərilərə push
    notify_on_start boolean    NOT NULL DEFAULT false,
    notified_at    timestamptz,
    created_at     timestamptz NOT NULL,
    created_by     uuid,
    updated_at     timestamptz NOT NULL,
    updated_by     uuid,
    CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CHECK (start_time IS NULL OR start_time < end_time),
    CHECK (starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on)
);

-- Bonus möhürlər: source = BONUS, hansı qaydaya aid olduğu ilə
ALTER TABLE stamps ADD COLUMN bonus_rule_id uuid REFERENCES bonus_rules (id);
ALTER TABLE stamps DROP CONSTRAINT stamps_source_check;
ALTER TABLE stamps ADD CONSTRAINT stamps_source_check
    CHECK (source IN ('NFC_TAP', 'MANUAL', 'CAMPAIGN', 'REFERRAL', 'BONUS'));
ALTER TABLE stamps ADD CONSTRAINT stamps_bonus_pair CHECK ((source = 'BONUS') = (bonus_rule_id IS NOT NULL));
CREATE INDEX stamps_bonus_rule_idx ON stamps (bonus_rule_id, created_at) WHERE bonus_rule_id IS NOT NULL;
