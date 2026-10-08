-- 2FA-nın müvəqqəti söndürülməsi (bax: tenant V5).
ALTER TABLE platform_admins ADD COLUMN totp_disabled_at timestamptz;
