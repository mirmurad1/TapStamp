-- SMS jurnalında kampaniya SMS-ləri də saxlanılır (hesabatda OTP və kampaniya ayrıca sayılır)
ALTER TABLE sms_messages DROP CONSTRAINT sms_messages_purpose_check;
ALTER TABLE sms_messages ADD CONSTRAINT sms_messages_purpose_check CHECK (purpose IN ('OTP', 'CAMPAIGN'));
