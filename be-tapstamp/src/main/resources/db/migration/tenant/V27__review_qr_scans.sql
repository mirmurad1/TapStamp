-- Google rəyi üçün çap olunan QR kart (stend): QR şirkətin domeninə (/api/v1/public/review/{filial}) aparır, oradan
-- filialın Google rəy səhifəsinə yönləndirilir. Skanlar gün üzrə sayılır — platforma kartın nə qədər işlədiyini görür.
CREATE TABLE review_qr_scans (
    branch_id uuid NOT NULL REFERENCES branches (id),
    day       date NOT NULL,
    scans     int  NOT NULL DEFAULT 0,
    PRIMARY KEY (branch_id, day)
);
