/** Kampaniyaların auditoriya seçimləri və status nişanlarının rəngi (şirkət və platforma panellərində ortaq). */
export const CAMPAIGN_AUDIENCES = ['ALL', 'INACTIVE', 'NEAR_REWARD', 'UNUSED_GIFT_CARD'] as const;
export type CampaignAudience = (typeof CAMPAIGN_AUDIENCES)[number] | 'EMPTY_CARD';

/** Kampaniya növü: adi SMS və ya möhür kampaniyası (SMS + hədiyyə möhür). */
export type CampaignType = 'SMS' | 'STAMPS';

/** "Uzun müddət gəlməyənlər" üçün gün seçimləri. */
export const INACTIVE_DAYS = [30, 60, 90] as const;

export const CAMPAIGN_STATUSES = ['PENDING_APPROVAL', 'APPROVED', 'SENDING', 'PAUSED', 'SENT', 'REJECTED', 'CANCELLED'] as const;
export type CampaignStatus = (typeof CAMPAIGN_STATUSES)[number];

/** API-dən gələn statusu tanınan dəyərə çevirir (tərcümə açarı üçün). */
export function campaignStatus(status: string | undefined): CampaignStatus {
  return CAMPAIGN_STATUSES.find((value) => value === status) ?? 'PENDING_APPROVAL';
}

export function campaignStatusTone(status: string | undefined): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'SENT':
      return 'success';
    case 'PENDING_APPROVAL':
    case 'APPROVED':
    case 'SENDING':
      return 'warning';
    case 'REJECTED':
    case 'PAUSED':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Önizləmə şablonunda müştəri adının yeri (backend-dəki CampaignService.NAME_TOKEN). */
export const NAME_TOKEN = '{name}';

/** Yalnız ASCII: SMS 160 simvollu (LATIN) formatda gedir; əks halda 70 simvol (UNICODE). */
export function isAscii(text: string): boolean {
  return [...text].every((ch) => ch.charCodeAt(0) < 128);
}

const LATIN_FROM = 'əƏşŞğĞıİçÇöÖüÜ';
const LATIN_TO = 'eEsSgGiIcCoOuU';

/** Azərbaycan hərflərinin latın qarşılığı (backend-dəki CampaignTexts.latin ilə eyni). */
export function toLatin(text: string): string {
  return [...text].map((ch) => (LATIN_FROM.includes(ch) ? LATIN_TO.charAt(LATIN_FROM.indexOf(ch)) : ch)).join('');
}