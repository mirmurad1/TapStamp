-- Müştəri səviyyələri (Bürünc, Gümüş, Qızıl və s.): ziyarət sayına (təsdiqlənmiş NFC möhürləri) görə avtomatik.
-- Səviyyə saxlanılmır — hər dəfə hesablanır, həddlər dəyişəndə dərhal tətbiq olunur. Bax: architecture-plan.md §18, #60.

CREATE TABLE loyalty_tiers (
    id           uuid PRIMARY KEY,
    name         jsonb       NOT NULL CHECK (name ? 'az'),
    color        text        NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    -- Bu səviyyəyə keçmək üçün lazım olan ziyarət sayı; hər səviyyədə fərqli
    min_visits   int         NOT NULL UNIQUE CHECK (min_visits BETWEEN 0 AND 10000),
    -- İmtiyaz: hər NFC möhürünə əlavə bonus möhür (0 — yalnız nişan)
    bonus_stamps int         NOT NULL CHECK (bonus_stamps BETWEEN 0 AND 3),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    updated_by   uuid
);

-- Səviyyə bonusu: source = TIER, hansı səviyyəyə görə verildiyi ilə
ALTER TABLE stamps ADD COLUMN tier_id uuid REFERENCES loyalty_tiers (id);
ALTER TABLE stamps DROP CONSTRAINT stamps_source_check;
ALTER TABLE stamps ADD CONSTRAINT stamps_source_check
    CHECK (source IN ('NFC_TAP', 'MANUAL', 'CAMPAIGN', 'REFERRAL', 'BONUS', 'TIER'));
ALTER TABLE stamps ADD CONSTRAINT stamps_tier_pair CHECK ((source = 'TIER') = (tier_id IS NOT NULL));
-- Ziyarət sayı: müştərinin təsdiqlənmiş NFC möhürləri
CREATE INDEX stamps_customer_visits_idx ON stamps (customer_id) WHERE source = 'NFC_TAP' AND status = 'CONFIRMED';

-- Proqramın açarı (şirkət üzrə tək sətir): söndürüləndə səviyyələr göstərilmir və bonus verilmir, məlumat qalır
CREATE TABLE tier_settings (
    id         smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled    boolean NOT NULL DEFAULT true,
    updated_at timestamptz,
    updated_by uuid
);
INSERT INTO tier_settings (id) VALUES (1);
