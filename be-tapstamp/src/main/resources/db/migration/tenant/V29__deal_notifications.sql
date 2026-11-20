-- Endirim bildirişləri: yeni endirimlər bir push-da elan olunur — şirkətin seçdiyi saatda avtomatik və ya sahib
-- "İndi göndər" düyməsi ilə. Şirkət gündə ən çox bir elan göndərir (deal_announcements.announced_on unikaldır),
-- müştəri gündə ən çox bir endirim bildirişi alır (deal_push_log). Bildiriş müştərinin filialının iş saatında çatır:
-- filial bağlıdırsa, bildiriş növbədə qalır və filial açılanda gedir (24 saat ərzində açılmasa, ləğv olunur).
CREATE TABLE deal_notification_settings (
    id               smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    auto_enabled     boolean NOT NULL DEFAULT true,
    -- Avtomatik göndərişin saatı (şirkətin saat qurşağında), 30 dəqiqəlik addımla
    send_time        time    NOT NULL DEFAULT '12:00'
        CHECK (extract(minute FROM send_time) IN (0, 30) AND extract(second FROM send_time) = 0),
    -- Endirimin son günü bir dəfə xatırlatma (həmin gün yeni endirim elanı yoxdursa)
    last_day_enabled boolean NOT NULL DEFAULT true,
    -- Avtomatik yoxlamanın sonuncu günü — gündə bir dəfə işləsin
    last_auto_run_on date,
    updated_at       timestamptz,
    updated_by       uuid
);
INSERT INTO deal_notification_settings (id) VALUES (1);

-- Hər endirim bir dəfə elan olunur; son gün xatırlatması da bir dəfə. Endirim yenidən qurulanda sıfırlanır.
ALTER TABLE catalog_items
    ADD COLUMN discount_announced_at          timestamptz,
    ADD COLUMN discount_last_day_announced_at timestamptz;

CREATE TABLE deal_announcements (
    id           uuid PRIMARY KEY,
    kind         text        NOT NULL CHECK (kind IN ('NEW', 'LAST_DAY')),
    trigger      text        NOT NULL CHECK (trigger IN ('AUTO', 'MANUAL')),
    -- Şirkətin günü (saat qurşağı ilə): gündə bir elan
    announced_on date        NOT NULL UNIQUE,
    item_count   int         NOT NULL CHECK (item_count > 0),
    -- Push xidmətinin qəbul etdiyi müştərilər (ən azı bir cihaz)
    delivered    int         NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL,
    created_by   uuid
);

-- Kimə göndərilir: sətir elan anında yaranır, push müştərinin filialı açıq olanda gedir (sent_at). Filial 24 saat
-- ərzində açılmasa — dropped_at. Mətn (siyahı) müştəriyə aid endirimlərlə elan anında hazırlanır.
CREATE TABLE deal_push_log (
    announcement_id uuid        NOT NULL REFERENCES deal_announcements (id),
    customer_id     uuid        NOT NULL REFERENCES customers (id),
    -- Elanın günü: müştəriyə gündə bir endirim bildirişi
    sent_on         date        NOT NULL,
    -- Müştərinin sonuncu filialı (null — istənilən filial açıq olanda)
    branch_id       uuid,
    item_count      int         NOT NULL CHECK (item_count > 0),
    items_text      jsonb       NOT NULL,
    created_at      timestamptz NOT NULL,
    sent_at         timestamptz,
    dropped_at      timestamptz,
    opened_at       timestamptz,
    PRIMARY KEY (announcement_id, customer_id),
    UNIQUE (customer_id, sent_on)
);
CREATE INDEX deal_push_log_customer_sent_idx ON deal_push_log (customer_id, sent_at);
CREATE INDEX deal_push_log_waiting_idx ON deal_push_log (created_at) WHERE sent_at IS NULL AND dropped_at IS NULL;
