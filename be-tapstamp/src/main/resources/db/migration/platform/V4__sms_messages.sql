-- Göndərilən bütün SMS-lərin jurnalı (platforma səviyyəsində, şirkətlər üzrə hesabat üçün).
-- Nömrə şifrəlidir (PII); mətndə OTP kodu gizlədilib ("Kafe OTP: ******") — jurnala baxan kod ilə daxil ola bilməz.
CREATE TABLE sms_messages (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenants (id),
    phone_enc     bytea       NOT NULL,
    phone_masked  text        NOT NULL,
    text          text        NOT NULL,
    purpose       text        NOT NULL CHECK (purpose IN ('OTP')),
    encoding      text        NOT NULL CHECK (encoding IN ('GSM_7', 'UCS_2')),
    segments      int         NOT NULL CHECK (segments > 0),
    status        text        NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    error         text,
    created_at    timestamptz NOT NULL
);

CREATE INDEX sms_messages_created_idx ON sms_messages (created_at);
CREATE INDEX sms_messages_tenant_created_idx ON sms_messages (tenant_id, created_at);
