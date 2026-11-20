-- Menyu məhsulu və ya xidmət üzrə endirim: faizlə (PERCENT, 1–99) və ya yeni qiymətlə (PRICE, köhnə qiymətdən aşağı).
-- Müddət şirkətin saat qurşağında tarixlərlə: discount_starts_on null — dərhal, discount_ends_on null — həmişəlik.
-- Endirim yalnız qiyməti olan məhsula verilir. Müştəri menyusunda yalnız bu gün qüvvədə olan endirim göstərilir.
ALTER TABLE catalog_items
    ADD COLUMN discount_kind      text CHECK (discount_kind IN ('PERCENT', 'PRICE')),
    ADD COLUMN discount_value     numeric(10, 2),
    ADD COLUMN discount_starts_on date,
    ADD COLUMN discount_ends_on   date,
    ADD CONSTRAINT catalog_items_discount_valid CHECK (
        (discount_kind IS NULL AND discount_value IS NULL AND discount_starts_on IS NULL AND discount_ends_on IS NULL)
        OR (discount_kind IS NOT NULL AND discount_value IS NOT NULL AND price IS NOT NULL
            AND (discount_kind <> 'PERCENT' OR discount_value BETWEEN 1 AND 99)
            AND (discount_kind <> 'PRICE' OR (discount_value >= 0 AND discount_value < price))
            AND (discount_starts_on IS NULL OR discount_ends_on IS NULL OR discount_starts_on <= discount_ends_on)));
