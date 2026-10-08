import { useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';

import { platformAuditApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const PAGE = 50;

/** Platforma audit jurnalı: şirkətlərin yaradılması, dayandırılması, domenlər, sahiblərin 2FA-sı. */
export function PlatformAuditPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const entries = useInfiniteQuery({
    queryKey: ['platform-audit'],
    queryFn: ({ pageParam }) => platformAuditApi.list({ before: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.length === PAGE ? last[last.length - 1]?.createdAt : undefined),
  });
  const items = entries.data?.pages.flat() ?? [];

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
          {entries.hasNextPage ? (
            <Button variant="outline" className="self-center" onClick={() => void entries.fetchNextPage()} disabled={entries.isFetchingNextPage}>
              {t('customers.timeline.more')}
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
