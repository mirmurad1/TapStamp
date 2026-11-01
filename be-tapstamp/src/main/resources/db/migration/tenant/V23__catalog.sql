-- Menyu və ya xidmətlər kataloqu (əlavə CATALOG, qərar #64): kateqoriyalar və onların məhsulları/xidmətləri.
-- Müştəriyə ictimai səhifədə göstərilir; sifariş və ödəniş yoxdur (yalnız kataloq).

-- Bölmənin parametrləri (şirkət üzrə tək sətir): açar və adı (restoran — "Menyu", digər sahələr — "Xidmətlər" və ya
-- "Qiymətlər"). Söndürüləndə müştəriyə göstərilmir, məlumat qalır.
CREATE TABLE catalog_settings (
    id         smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled    boolean NOT NULL DEFAULT true,
    title      text    NOT NULL DEFAULT 'MENU' CHECK (title IN ('MENU', 'SERVICES', 'PRICES')),
    updated_at timestamptz,
    updated_by uuid
);
INSERT INTO catalog_settings (id) VALUES (1);

CREATE TABLE catalog_categories (
    id         uuid PRIMARY KEY,
    name       jsonb       NOT NULL CHECK (name ? 'az'),
    -- Siyahıdakı yer (kiçikdən böyüyə); sıralama sürüklə-burax ilə dəyişir
    position   int         NOT NULL,
    -- Gizlədilmiş kateqoriya müştəriyə göstərilmir (silmədən)
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by uuid
);

CREATE TABLE catalog_items (
    id               uuid PRIMARY KEY,
    category_id      uuid          NOT NULL REFERENCES catalog_categories (id),
    name             jsonb         NOT NULL CHECK (name ? 'az'),
    description      jsonb         CHECK (description IS NULL OR description ? 'az'),
    -- Qiymət (AZN); null — qiymət göstərilmir (məs. "razılaşma ilə")
    price            numeric(10, 2) CHECK (price IS NULL OR price BETWEEN 0 AND 100000),
    -- Xidmətin müddəti (dəqiqə), istəyə bağlı: "Saç kəsimi · 30 dəq"
    duration_minutes int           CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440),
    -- Şəkil: kvadrat PNG (512 px-ə qədər); image_version hər dəyişiklikdə artır — ictimai ünvan versiyalıdır
    image            bytea         CHECK (image IS NULL OR octet_length(image) <= 2097152),
    image_version    int           NOT NULL DEFAULT 0,
    -- Müvəqqəti yoxdur ("Hazırda yoxdur"): müştəriyə solğun göstərilir, silinmir
    available        boolean       NOT NULL DEFAULT true,
    -- Yalnız bu filialda (null — bütün filiallarda)
    branch_id        uuid          REFERENCES branches (id),
    position         int           NOT NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    updated_by       uuid
);
CREATE INDEX catalog_items_category_idx ON catalog_items (category_id, position);
