-- Paketlər (tariflər) və sınaq müddəti (qərar #57).

-- Şirkətin cari paketi. Sətir yoxdursa — Premium, ödənişli (paketlərdən əvvəl yaradılmış şirkətlər).
CREATE TABLE tenant_subscriptions (
    tenant_id      uuid PRIMARY KEY REFERENCES tenants (id),
    plan           text        NOT NULL CHECK (plan IN ('BASIC', 'STANDARD', 'PREMIUM')),
    status         text        NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE')),
    trial_ends_at  timestamptz,
    extra_branches int         NOT NULL DEFAULT 0 CHECK (extra_branches BETWEEN 0 AND 100),
    updated_at     timestamptz NOT NULL,
    updated_by     uuid,
    CHECK ((status = 'TRIAL') = (trial_ends_at IS NOT NULL)),
    CHECK (plan = 'PREMIUM' OR extra_branches = 0)
);

-- İstifadə olunmuş sınaq müddətləri: hər paket üçün şirkətə yalnız bir dəfə (sui-istifadənin qarşısı)
CREATE TABLE tenant_plan_trials (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    plan       text        NOT NULL CHECK (plan IN ('BASIC', 'STANDARD', 'PREMIUM')),
    started_at timestamptz NOT NULL,
    ends_at    timestamptz NOT NULL,
    started_by uuid,
    PRIMARY KEY (tenant_id, plan)
);

-- Mövcud şirkətlər Premium-a (ödənişli) keçirilir — indi işləyən heç nə bağlanmır
INSERT INTO tenant_subscriptions (tenant_id, plan, status, trial_ends_at, extra_branches, updated_at)
SELECT id, 'PREMIUM', 'ACTIVE', NULL, 0, now() FROM tenants;
