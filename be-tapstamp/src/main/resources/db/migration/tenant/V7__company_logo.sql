-- Şirkətin loqosu (müştəri tətbiqinin başlığında). PNG kimi saxlanılır; NULL — standart ikon göstərilir.
-- logo_version hər dəyişiklikdə artır: ictimai ünvan versiyalıdır (?v=N), brauzer keşi köhnə şəkli göstərmir.
ALTER TABLE company_profile
    ADD COLUMN logo bytea CHECK (logo IS NULL OR octet_length(logo) <= 2097152),
    ADD COLUMN logo_version integer NOT NULL DEFAULT 0;
