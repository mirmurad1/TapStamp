-- Kampaniya göndərişinin etibarlılığı və möhür kampaniyası (qərar 50, docs/campaigns.md).

-- Avtomatik təkrar: müvəqqəti xətada alıcı növbəyə qayıdır (10 dəq, 30 dəq, 2 saat sonra; ən çox 4 cəhd)
ALTER TABLE campaign_recipients ADD COLUMN attempts integer NOT NULL DEFAULT 0;
ALTER TABLE campaign_recipients ADD COLUMN next_attempt_at timestamptz;
-- Möhür kampaniyası: möhür SMS qəbul olunanda, eyni tranzaksiyada verilir (bir dəfə)
ALTER TABLE campaign_recipients ADD COLUMN stamps_granted_at timestamptz;
DROP INDEX campaign_recipients_pending_idx;
CREATE INDEX campaign_recipients_pending_idx ON campaign_recipients (campaign_id, next_attempt_at) WHERE status = 'PENDING';

-- Hesab səviyyəli xətada (icazə, balans) kampaniya dayandırılır; platforma admini "Davam et" ilə bərpa edir.
-- Təkrar cəhdlər sonsuz olmasın: göndəriş başlayandan 48 saat sonra qalanlar son olaraq xəta olur.
ALTER TABLE campaigns DROP CONSTRAINT campaigns_status_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check CHECK (status IN
    ('PENDING_APPROVAL', 'APPROVED', 'SENDING', 'PAUSED', 'SENT', 'REJECTED', 'CANCELLED'));
ALTER TABLE campaigns ADD COLUMN pause_reason text;
ALTER TABLE campaigns ADD COLUMN deadline_at timestamptz;

-- Möhür kampaniyası: kartı boş olan aktiv müştərilərə SMS + möhür
ALTER TABLE campaigns ADD COLUMN type text NOT NULL DEFAULT 'SMS' CHECK (type IN ('SMS', 'STAMPS'));
ALTER TABLE campaigns ADD COLUMN stamp_count integer CHECK (stamp_count IS NULL OR stamp_count BETWEEN 1 AND 49);
ALTER TABLE campaigns ADD CONSTRAINT campaigns_stamps_pair CHECK ((type = 'STAMPS') = (stamp_count IS NOT NULL));
ALTER TABLE campaigns DROP CONSTRAINT campaigns_audience_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_audience_check CHECK (audience IN
    ('ALL', 'INACTIVE', 'NEAR_REWARD', 'UNUSED_GIFT_CARD', 'EMPTY_CARD'));

-- Kampaniya möhürü: mənbə CAMPAIGN, hansı kampaniyadan verildiyi saxlanılır
ALTER TABLE stamps ADD COLUMN campaign_id uuid REFERENCES campaigns (id);
ALTER TABLE stamps DROP CONSTRAINT stamps_source_check;
ALTER TABLE stamps ADD CONSTRAINT stamps_source_check CHECK (source IN ('NFC_TAP', 'MANUAL', 'CAMPAIGN'));
ALTER TABLE stamps ADD CONSTRAINT stamps_campaign_pair CHECK ((source = 'CAMPAIGN') = (campaign_id IS NOT NULL));
