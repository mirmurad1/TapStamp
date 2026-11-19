import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { AuditTable } from '../audit/AuditPage';

/** Platforma panelində şirkətin öz audit jurnalı: işçilərin, sistemin və platformanın əməliyyatları (yalnız oxuma). */
export function TenantAuditCard({ tenantId }: { tenantId: string }) {
  const { t } = useTranslation('admin');
  const [action, setAction] = useState('');
  const filter = { action: action || undefined };
  const actions = useQuery({
    queryKey: ['platform-tenant-audit-actions', tenantId],
    queryFn: () => platformApi.auditActions(tenantId),
  });
  const paging = usePagination(filter);
  const entries = useQuery({
    queryKey: ['platform-tenant-audit', tenantId, filter, paging.page, paging.size],
    queryFn: () => platformApi.auditLog(tenantId, { ...filter, page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = entries.data?.items ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('platform.detail.audit')}</CardTitle>
        <CardDescription>{t('platform.detail.auditHint')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 self-start text-sm font-medium">
          {t('audit.filters.action')}
          <Select value={action} onChange={(event) => setAction(event.target.value)}>
            <option value="">{t('customers.filters.any')}</option>
            {actions.data?.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </Select>
        </label>
        <ApiErrorAlert error={entries.error ?? actions.error} />
        {entries.isPending ? <InlineLoading /> : null}
        {entries.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('audit.empty')}</p>
        ) : null}
        {items.length > 0 ? <AuditTable items={items} /> : null}
        <Pagination state={paging} total={entries.data?.total ?? 0} />
      </CardContent>
    </Card>
  );
}
