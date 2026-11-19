-- Menyu şəkillərinin kiçik variantı: siyahıda şəkil 88 px göstərilir, 640 px-lik əsas şəkli yükləmək mobil internetdə
-- menyunu saniyələrlə gecikdirirdi. image_thumb — 192 px JPEG (təxminən 10–15 KB). Köhnə şəkillər üçün ilk sorğuda
-- yaradılır (CatalogService.thumbnail), ayrıca köçürmə lazım deyil. Əsas şəkil yeni yükləmələrdə JPEG-dir, köhnələr PNG
-- qalır (ictimai endpoint növü baytlardan müəyyən edir).
ALTER TABLE catalog_items
    ADD COLUMN image_thumb bytea CHECK (image_thumb IS NULL OR octet_length(image_thumb) <= 262144);
