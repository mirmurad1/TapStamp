-- Başlanğıc siyahısı (admin panelinin göstəricilər səhifəsi): addımların vəziyyəti mövcud məlumatdan hesablanır.
-- Burada sahibin "müştəri cəlbi tövsiyələrini gizlət" seçimi və loqo addımının ilk tamamlanma anı saxlanılır: loqo
-- sonradan silinsə də addım tamamlanmış qalır (standart loqo görünür, tətbiqin işinə mane deyil). Digər addımlar canlı
-- hesablanır — məs. bütün filiallar silinsə, kart yenidən görünür.
CREATE TABLE setup_state (
    id                        smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    recommendations_hidden_at timestamptz,
    branding_done_at          timestamptz,
    updated_by                uuid
);
INSERT INTO setup_state (id) VALUES (1);
