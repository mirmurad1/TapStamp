-- Google rəyinə yönləndirmə: rəy verən müştəriyə filialın Google Maps rəy səhifəsi təklif olunur. Link filial üzrədir
-- (hər filialın Google-da ayrıca kartı var); yalnız Google ünvanları qəbul olunur (servis yoxlayır).
ALTER TABLE branches
    ADD COLUMN google_review_url text
        CHECK (google_review_url IS NULL OR (length(google_review_url) <= 500 AND google_review_url LIKE 'https://%'));

-- Müştəri Google-a keçidi açdı (sahibə nəticəni göstərmək üçün: "Google-a yönləndirildi")
ALTER TABLE feedback ADD COLUMN review_clicked_at timestamptz;
CREATE INDEX feedback_review_clicked_idx ON feedback (review_clicked_at) WHERE review_clicked_at IS NOT NULL;
