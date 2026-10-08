import { useTranslation } from 'react-i18next';

import { Badge } from '@/shared/ui/badge';

const STATUSES = ['ACTIVE', 'SUSPENDED', 'PROVISIONING', 'MIGRATION_FAILED', 'DECOMMISSIONED'] as const;
type Status = (typeof STATUSES)[number];

function isStatus(value: string | undefined): value is Status {
  return (STATUSES as readonly (string | undefined)[]).includes(value);
}

export function TenantStatusBadge({ status }: { status: string | undefined }) {
  const { t } = useTranslation('admin');
  if (!isStatus(status)) {
    return null;
  }
  const tone = status === 'ACTIVE' ? 'success' : status === 'PROVISIONING' ? 'warning' : 'danger';
  return <Badge tone={tone}>{t(`platform.status.${status}`)}</Badge>;
}
