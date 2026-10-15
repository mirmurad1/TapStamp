-- SMS jurnalı (istifadəçi qərarı): nömrə açıq saxlanılır; mətndə OTP kodu "******" kimi qalır, kodun özü isə ayrıca
-- sütunda şifrəlidir (code_enc). Əvvəlki sətirlərdə tam nömrə yalnız şifrəli idi — onlar üçün maskalı nömrə qalır.
ALTER TABLE sms_messages ADD COLUMN phone text;
UPDATE sms_messages SET phone = phone_masked;
ALTER TABLE sms_messages ALTER COLUMN phone SET NOT NULL;
ALTER TABLE sms_messages DROP COLUMN phone_enc;
ALTER TABLE sms_messages DROP COLUMN phone_masked;
ALTER TABLE sms_messages ADD COLUMN code_enc bytea;
CREATE INDEX sms_messages_phone_idx ON sms_messages (phone, created_at);
