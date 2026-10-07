-- İşçinin silinməsi yumşaq silinmədir (soft delete): sətir qalır (audit, hədiyyə təhvili tarixçəsi), status DELETED olur.
-- Silinmiş işçinin email-i yenidən dəvət üçün boşalır — unikallıq yalnız silinməmiş hesablar arasındadır.
ALTER TABLE staff_users DROP CONSTRAINT staff_users_status_check;
ALTER TABLE staff_users ADD CONSTRAINT staff_users_status_check
    CHECK (status IN ('ACTIVE', 'DISABLED', 'INVITED', 'DELETED'));
ALTER TABLE staff_users ADD COLUMN deleted_at timestamptz;
ALTER TABLE staff_users ADD COLUMN deleted_by uuid REFERENCES staff_users (id);
ALTER TABLE staff_users ADD CONSTRAINT staff_users_deleted_pair
    CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
ALTER TABLE staff_users DROP CONSTRAINT staff_users_email_key;
CREATE UNIQUE INDEX staff_users_email_live ON staff_users (email) WHERE status <> 'DELETED';

-- 2FA-nın müvəqqəti söndürülməsi: sirr şifrəli saxlanılır, yenidən aktivləşdirmə eyni tətbiqin kodu ilə olur.
ALTER TABLE staff_users ADD COLUMN totp_disabled_at timestamptz;
