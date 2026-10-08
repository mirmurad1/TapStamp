-- Müştəri autentifikasiyası üçün əlavələr.

-- OTP sorğusunun məqsədi: giriş (LOGIN) və ya həssas əməliyyatdan əvvəl təkrar təsdiq (STEP_UP, məs. hesabın silinməsi).
-- STEP_UP sorğusu konkret müştəriyə bağlıdır.
ALTER TABLE otp_challenges
    ADD COLUMN purpose     text NOT NULL DEFAULT 'LOGIN' CHECK (purpose IN ('LOGIN', 'STEP_UP')),
    ADD COLUMN customer_id uuid REFERENCES customers (id);
ALTER TABLE otp_challenges
    ADD CONSTRAINT otp_challenges_step_up_customer CHECK (purpose = 'LOGIN' OR customer_id IS NOT NULL);
CREATE INDEX otp_challenges_expiry_idx ON otp_challenges (expires_at) WHERE status = 'ACTIVE';

-- Sessiyada son OTP təkrar təsdiqinin vaxtı (10 dəqiqə etibarlıdır)
ALTER TABLE customer_sessions ADD COLUMN step_up_at timestamptz;
