-- Səviyyəyə çatanda bir dəfəlik imtiyazlar (qərar #60): şirkətin cari hədiyyəsi və/və ya bir neçə möhür.
-- Hər müştəriyə hər səviyyə üçün bir dəfə (tier_reach_grants) — hədd dəyişsə də, müştəri düşüb yenidən qalxsa da.

ALTER TABLE loyalty_tiers
    ADD COLUMN reach_gift   boolean NOT NULL DEFAULT false,
    ADD COLUMN reach_stamps int     NOT NULL DEFAULT 0 CHECK (reach_stamps BETWEEN 0 AND 10);

CREATE TABLE tier_reach_grants (
    customer_id uuid        NOT NULL REFERENCES customers (id),
    tier_id     uuid        NOT NULL REFERENCES loyalty_tiers (id),
    reward_id   uuid REFERENCES rewards (id),
    granted_at  timestamptz NOT NULL,
    PRIMARY KEY (customer_id, tier_id)
);

-- Hədiyyə kartının səbəbi: CARD — möhür kartı doldu; TIER — səviyyəyə çatdı (səviyyənin adı snapshot kimi)
ALTER TABLE rewards
    ADD COLUMN source            text NOT NULL DEFAULT 'CARD' CHECK (source IN ('CARD', 'TIER')),
    ADD COLUMN tier_name_snapshot jsonb,
    ADD CONSTRAINT rewards_tier_name CHECK ((source = 'TIER') = (tier_name_snapshot IS NOT NULL));
