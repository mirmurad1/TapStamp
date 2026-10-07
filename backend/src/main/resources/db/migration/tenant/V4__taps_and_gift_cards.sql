-- NFC toxunuşu, möhürlər və hədiyyə kartları (Mərhələ 5).

-- Hədiyyə kartı (rewards sətri): daimi unikal kod. Kodun özü saxlanılmır — o, tenant açarı ilə rewards.id və
-- code_nonce-dan törədilir; axtarış üçün yalnız HMAC-ı (code_lookup) saxlanılır. Baza sızsa belə kodlar bilinmir.
ALTER TABLE rewards
    ADD COLUMN code_nonce         integer,
    ADD COLUMN code_lookup        bytea UNIQUE,
    ADD COLUMN expires_at         timestamptz,
    ADD COLUMN redeemed_by        uuid REFERENCES staff_users (id),
    ADD COLUMN redeemed_branch_id uuid REFERENCES branches (id);
ALTER TABLE rewards DROP CONSTRAINT rewards_status_check;
ALTER TABLE rewards
    ADD CONSTRAINT rewards_status_check CHECK (status IN ('AVAILABLE', 'REDEEMED', 'REVOKED', 'EXPIRED')),
    ADD CONSTRAINT rewards_code_pair CHECK ((code_nonce IS NULL) = (code_lookup IS NULL));
CREATE INDEX rewards_expiry_idx ON rewards (expires_at) WHERE status = 'AVAILABLE' AND expires_at IS NOT NULL;

-- Hədiyyə kartının istifadə müddəti (gün); NULL — müddətsiz
ALTER TABLE reward_rules
    ADD COLUMN validity_days integer CHECK (validity_days IS NULL OR validity_days BETWEEN 1 AND 3650);

-- Şirkətin brend rəngi (müştəri tətbiqinin əsas rəngi və möhür ikonları)
ALTER TABLE company_profile
    ADD COLUMN brand_color text NOT NULL DEFAULT '#7C3AED' CHECK (brand_color ~ '^#[0-9A-Fa-f]{6}$');

-- Claim sorğuları: müştərinin son möhürü (cooldown) və bir çipdə/brauzerdə çoxlu hesab analitikası
CREATE INDEX stamps_tap_recent_idx ON stamps (customer_id, created_at DESC)
    WHERE source = 'NFC_TAP' AND status IN ('PENDING', 'CONFIRMED');
CREATE INDEX tap_events_claim_tag_idx ON tap_events (tag_id, created_at DESC) WHERE stage = 'CLAIM';
CREATE INDEX tap_events_browser_idx ON tap_events (browser_id_hash, created_at DESC) WHERE browser_id_hash IS NOT NULL;
CREATE INDEX tap_tokens_reserved_idx ON tap_tokens (reserved_challenge_id) WHERE reserved_challenge_id IS NOT NULL;
