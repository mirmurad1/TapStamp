-- İşçi autentifikasiyası üçün əlavələr.

-- Bir sessiyada uğursuz TOTP cəhdləri (5-dən sonra sessiya ləğv olunur)
ALTER TABLE staff_sessions ADD COLUMN mfa_failed_attempts integer NOT NULL DEFAULT 0;

-- Dəvət emailinin dili (dəvət edənin seçdiyi)
ALTER TABLE staff_invitations
    ADD COLUMN language text NOT NULL DEFAULT 'az' CHECK (language IN ('az', 'en', 'ru'));
CREATE INDEX staff_invitations_email_idx ON staff_invitations (email) WHERE accepted_at IS NULL AND revoked_at IS NULL;
