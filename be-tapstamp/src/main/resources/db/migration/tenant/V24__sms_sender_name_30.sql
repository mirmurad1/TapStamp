-- "SMS-də görünən ad" 11 yox, 30 simvola qədər: şirkətin adı uzun ola bilər. Bu ad SMS-in mətnindədir (göndərən adı
-- bütün şirkətlər üçün TrendStamp-dır), ona görə alfa-adın 11 simvol həddi burada lazım deyil. OTP SMS-i ad uzun
-- olsa da 1 seqment qalır — ad lazım olduqca qısaldılır (SmsTemplates.otp).
ALTER TABLE company_profile DROP CONSTRAINT IF EXISTS company_profile_sms_sender_name_check;
ALTER TABLE company_profile ADD CONSTRAINT company_profile_sms_sender_name_check
    CHECK (sms_sender_name IS NULL OR sms_sender_name ~ '^[A-Za-z0-9 ]{1,30}$');
