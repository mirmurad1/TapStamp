-- SMS limitləri (qərar #53).

-- Şirkətin gündəlik limiti (yeni nömrələrə giriş kodları); sətir yoxdursa standart dəyər (app.sms-limits)
CREATE TABLE tenant_sms_limits (
    tenant_id            uuid PRIMARY KEY REFERENCES tenants (id),
    daily_new_otp_limit  int         NOT NULL CHECK (daily_new_otp_limit BETWEEN 10 AND 100000),
    updated_at           timestamptz NOT NULL,
    updated_by           uuid
);

-- Limit xəbərdarlıqları: gün, sahə (platform və ya tenant:<id>) və səviyyə (80, 100) üzrə bir dəfə
CREATE TABLE sms_limit_alerts (
    day        date        NOT NULL,
    scope      text        NOT NULL,
    level      int         NOT NULL CHECK (level IN (80, 100)),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (day, scope, level)
);
