-- Dost dəvəti (referral — müştəri dostunu dəvət edir; dost ilk NFC möhürünü alanda hər ikisi bonus möhür qazanır).
-- Bax: docs/architecture-plan.md §18, qərar #52.

-- Proqramın parametrləri (şirkət üzrə tək sətir); default olaraq söndürülüb
CREATE TABLE referral_settings (
    id              smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled         boolean     NOT NULL DEFAULT false,
    referrer_stamps int         NOT NULL DEFAULT 1 CHECK (referrer_stamps BETWEEN 1 AND 3),
    friend_stamps   int         NOT NULL DEFAULT 1 CHECK (friend_stamps BETWEEN 1 AND 3),
    monthly_cap     int         NOT NULL DEFAULT 5 CHECK (monthly_cap BETWEEN 1 AND 50),
    valid_days      int         NOT NULL DEFAULT 30 CHECK (valid_days BETWEEN 7 AND 90),
    updated_at      timestamptz,
    updated_by      uuid
);
INSERT INTO referral_settings (id) VALUES (1);

-- Müştərinin şəxsi dəvət kodu (ilk dəfə istəniləndə yaranır)
ALTER TABLE customers ADD COLUMN referral_code text UNIQUE CHECK (referral_code ~ '^[A-Z2-9]{8}$');

CREATE TABLE referrals (
    id            uuid PRIMARY KEY,
    referrer_id   uuid        NOT NULL REFERENCES customers (id),
    -- Bir müştəri yalnız bir dəfə dəvət oluna bilər (qeydiyyat anında)
    friend_id     uuid        NOT NULL UNIQUE REFERENCES customers (id),
    -- PENDING — dost hələ gəlməyib; REWARDED — bonuslar verilib; LIMIT_REACHED — dəvət edənin aylıq limiti dolmuşdu
    status        text        NOT NULL CHECK (status IN ('PENDING', 'REWARDED', 'LIMIT_REACHED')),
    created_at    timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    rewarded_at   timestamptz,
    CHECK (referrer_id <> friend_id),
    CHECK ((status = 'REWARDED') = (rewarded_at IS NOT NULL))
);
CREATE INDEX referrals_referrer_idx ON referrals (referrer_id, created_at);
CREATE INDEX referrals_created_idx ON referrals (created_at DESC, id DESC);

-- Bonus möhürlər: source = REFERRAL, hansı dəvətə aid olduğu ilə
ALTER TABLE stamps ADD COLUMN referral_id uuid REFERENCES referrals (id);
ALTER TABLE stamps DROP CONSTRAINT stamps_source_check;
ALTER TABLE stamps ADD CONSTRAINT stamps_source_check CHECK (source IN ('NFC_TAP', 'MANUAL', 'CAMPAIGN', 'REFERRAL'));
ALTER TABLE stamps ADD CONSTRAINT stamps_referral_pair CHECK ((source = 'REFERRAL') = (referral_id IS NOT NULL));
