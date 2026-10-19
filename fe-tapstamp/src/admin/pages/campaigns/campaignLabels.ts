/** Kampaniyaların auditoriya seçimləri və status nişanlarının rəngi (şirkət və platforma panellərində ortaq). */
export const CAMPAIGN_AUDIENCES = ['ALL', 'INACTIVE', 'NEAR_REWARD', 'UNUSED_GIFT_CARD'] as const;
export type CampaignAudience = (typeof CAMPAIGN_AUDIENCES)[number];

/** "Uzun müddət gəlməyənlər" üçün gün seçimləri. */
export const INACTIVE_DAYS = [30, 60, 90] as const;

export const CAMPAIGN_STATUSES = ['PENDING_APPROVAL', 'APPROVED', 'SENDING', 'SENT', 'REJECTED', 'CANCELLED'] as const;
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
      return 'danger';
    default:
      return 'neutral';
  }
}
