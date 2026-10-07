import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Input, Select } from '@/shared/ui/input';

import { auditApi, type AuditEntry } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const PAGE = 50;

/** Audit jurnalı (yalnız sahib): kim, nə vaxt, nə etdi. Jurnal dəyişdirilə bilməz. */
export function AuditPage() {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const filter = { action: action || undefined, from: from || undefined, to: to || undefined };

  const actions = useQuery({ queryKey: ['audit-actions'], queryFn: auditApi.actions });
  const entries = useInfiniteQuery({
    queryKey: ['audit', filter],
    queryFn: ({ pageParam }) => auditApi.list({ ...filter, before: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.length === PAGE ? last[last.length - 1]?.createdAt : undefined),
  });
  const items = entries.data?.pages.flat() ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('admin:audit.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('admin:audit.subtitle')}</p>
      </div>
      <Card>
        <CardContent className="flex flex-col gap-4 pt-5 sm:pt-6">
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:audit.filters.action')}
              <Select value={action} onChange={(event) => setAction(event.target.value)}>
                <option value="">{t('admin:customers.filters.any')}</option>
                {actions.data?.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </Select>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:audit.filters.from')}
              <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:audit.filters.to')}
              <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
            </label>
          </div>
          <ApiErrorAlert error={entries.error ?? actions.error} />
          {entries.isPending ? <InlineLoading /> : null}
          {entries.data && items.length === 0 ? <p className="py-6 text-center text-sm text-muted-foreground">{t('admin:audit.empty')}</p> : null}
          {items.length > 0 ? (
            <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
              <table className="w-full min-w-[48rem] text-left text-sm">
                <thead className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('admin:audit.columns.time')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('admin:audit.columns.actor')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('admin:audit.columns.action')}</th>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('admin:audit.columns.details')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border align-top">
                  {items.map((entry) => (
                    <AuditRow key={entry.id} entry={entry} lang={lang} />
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          {entries.hasNextPage ? (
            <Button variant="outline" className="self-center" onClick={() => void entries.fetchNextPage()} disabled={entries.isFetchingNextPage}>
              {t('admin:customers.timeline.more')}
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

function AuditRow({ entry, lang }: { entry: AuditEntry; lang: ReturnType<typeof currentLanguage> }) {
  const { t } = useTranslation('admin');
  const details = entry.details && entry.details !== '{}' ? entry.details : null;
  return (
    <tr>
      <td className="whitespace-nowrap px-5 py-3 text-muted-foreground sm:px-6">
        {entry.createdAt ? formatDateTime(entry.createdAt, lang) : null}
      </td>
      <td className="px-3 py-3">
        <p className="font-medium">{entry.actorName ?? t(`audit.actorType.${(entry.actorType ?? 'SYSTEM') as 'SYSTEM'}`)}</p>
        {entry.ip ? <p className="text-xs text-muted-foreground">{entry.ip}</p> : null}
      </td>
      <td className="px-3 py-3">
        <Badge>{entry.action}</Badge>
        {entry.targetType ? (
          <p className="mt-1 text-xs text-muted-foreground">
            {entry.targetType}
            {entry.targetId ? ` · ${entry.targetId.slice(0, 8)}` : null}
          </p>
        ) : null}
      </td>
      <td className="px-5 py-3 sm:px-6">
        {details ? <code className="block max-w-md break-all text-xs text-muted-foreground">{details}</code> : null}
      </td>
    </tr>
  );
}
