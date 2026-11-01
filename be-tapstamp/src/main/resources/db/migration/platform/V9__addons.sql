-- Əlavələr (add-on): paketdən asılı olmayan, ayrıca satılan bölmələr (qərar #64). İlk əlavə — CATALOG (menyu və ya
-- xidmətlər). Sətir varsa, əlavə açıqdır; bağlananda sətir silinir (tarixçə platforma audit jurnalındadır).
CREATE TABLE tenant_addons (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    addon      text        NOT NULL CHECK (addon IN ('CATALOG')),
    enabled_at timestamptz NOT NULL,
    enabled_by uuid,
    PRIMARY KEY (tenant_id, addon)
);
