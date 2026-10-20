import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Card, CardContent } from '@/shared/ui/card';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformAuditApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

/** Platforma audit jurnalı: şirkətlərin yaradılması, dayandırılması, domenlər, sahiblərin 2FA-sı. */
export function PlatformAuditPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
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
          <ul className="flex flex-col divide-y divide-border">
            {items.map((entry) => (
              <li key={entry.id} className="flex flex-wrap items-start justify-between gap-3 py-3 text-sm">
                <div className="min-w-0">
                  <Badge>{entry.action}</Badge>
                  <p className="mt-1 text-muted-foreground">
                    {entry.actorName ?? t('audit.actorType.SYSTEM')}
                    {entry.ip ? ` · ${entry.ip}` : null}
                  </p>
                  {entry.details && entry.details !== '{}' ? (
                    <code className="mt-1 block break-all text-xs text-muted-foreground">{entry.details}</code>
                  ) : null}
                </div>
                <span className="whitespace-nowrap text-muted-foreground">
                  {entry.createdAt ? formatDateTime(entry.createdAt, lang) : null}
                </span>
              </li>
            ))}
          </ul>
          <Pagination state={paging} total={entries.data?.total ?? 0} />
        </CardContent>
      </Card>
    </div>
  );
}
