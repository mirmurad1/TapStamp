-- Dost dəvəti: bonusun nə vaxt veriləcəyi şirkətin seçimidir (qərar #52).
-- FIRST_VISIT — dostun ilk təsdiqlənmiş NFC möhüründə (default); REGISTRATION — dost qeydiyyatdan keçən kimi.
ALTER TABLE referral_settings
    ADD COLUMN reward_on text NOT NULL DEFAULT 'FIRST_VISIT' CHECK (reward_on IN ('FIRST_VISIT', 'REGISTRATION'));
