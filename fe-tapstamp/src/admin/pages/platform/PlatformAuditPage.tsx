import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { Card, CardContent } from '@/shared/ui/card';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformAuditApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { AuditTable } from '../audit/AuditPage';

/** Platforma audit jurnalı: şirkətlərin yaradılması, dayandırılması, domenlər, sahiblərin 2FA-sı. */
export function PlatformAuditPage() {
  const { t } = useTranslation('admin');
  const paging = usePagination();
  const entries = useQuery({
    queryKey: ['platform-audit', paging.page, paging.size],
    queryFn: () => platformAuditApi.list({ page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = entries.data?.items ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('platform.audit.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('platform.audit.subtitle')}</p>
      </div>
      <Card>
        <CardContent className="flex flex-col gap-3 pt-5 sm:pt-6">
          <ApiErrorAlert error={entries.error} />
          {entries.isPending ? <InlineLoading /> : null}
          {entries.data && items.length === 0 ? <p className="py-6 text-center text-sm text-muted-foreground">{t('audit.empty')}</p> : null}
          {items.length > 0 ? <AuditTable items={items} /> : null}
          <Pagination state={paging} total={entries.data?.total ?? 0} />
        </CardContent>
      </Card>
    </div>
  );
}
