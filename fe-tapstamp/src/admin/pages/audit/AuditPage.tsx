import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Card, CardContent } from '@/shared/ui/card';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { auditApi, type AuditEntry } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

/** Audit jurnalı (yalnız sahib): kim, nə vaxt, nə etdi. Jurnal dəyişdirilə bilməz. */
export function AuditPage() {
  const { t } = useTranslation(['admin', 'common']);
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const filter = { action: action || undefined, from: from || undefined, to: to || undefined };

  const actions = useQuery({ queryKey: ['audit-actions'], queryFn: auditApi.actions });
  const paging = usePagination(filter);
  const entries = useQuery({
    queryKey: ['audit', filter, paging.page, paging.size],
    queryFn: () => auditApi.list({ ...filter, page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = entries.data?.items ?? [];

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
                    {actionLabel(t, name)}
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
          {items.length > 0 ? <AuditTable items={items} /> : null}
          <Pagination state={paging} total={entries.data?.total ?? 0} />
        </CardContent>
      </Card>
    </div>
  );
}

/** Jurnal qeydlərinin cədvəli — şirkətin öz panelində və platforma panelində (şirkətin səhifəsində) eynidir. */
export function AuditTable({ items }: { items: AuditEntry[] }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  return (
    <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
      <table className="w-full min-w-[48rem] text-left text-sm">
        <thead className="border-b border-border text-xs text-muted-foreground">
          <tr>
            <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('audit.columns.time')}</th>
            <th scope="col" className="px-3 py-2 font-medium">{t('audit.columns.actor')}</th>
            <th scope="col" className="px-3 py-2 font-medium">{t('audit.columns.action')}</th>
            <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('audit.columns.details')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border align-top">
          {items.map((entry) => (
            <AuditRow key={entry.id} entry={entry} lang={lang} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditRow({ entry, lang }: { entry: AuditEntry; lang: ReturnType<typeof currentLanguage> }) {
  const { t } = useTranslation('admin');
  const details = parseDetails(entry.details);
  return (
    <tr>
      <td className="whitespace-nowrap px-5 py-3 text-muted-foreground sm:px-6">
        {entry.createdAt ? formatDateTime(entry.createdAt, lang) : null}
      </td>
      <td className="px-3 py-3">
        <p className="font-medium">{entry.actorName ?? t(`audit.actorType.${(entry.actorType ?? 'SYSTEM') as 'SYSTEM'}`)}</p>
        {entry.ip ? <p className="text-xs text-muted-foreground">{LOCAL_IPS.has(entry.ip) ? t('audit.localIp') : entry.ip}</p> : null}
      </td>
      <td className="px-3 py-3">
        <Badge tone={actionTone(entry.action ?? '')} title={entry.action}>
          {actionLabel(t, entry.action ?? '')}
        </Badge>
        {entry.targetType ? (
          <p className="mt-1 text-xs text-muted-foreground">
            {t(`audit.targets.${entry.targetType}`, { defaultValue: entry.targetType })}
            {entry.targetId ? ` · ${entry.targetId.slice(0, 8)}` : null}
          </p>
        ) : null}
      </td>
      <td className="px-5 py-3 sm:px-6">
        {details ? (
          <dl className="flex max-w-md flex-col gap-0.5 text-xs">
            {details.map(([key, value]) => (
              <div key={key} className="flex gap-1.5">
                <dt className="shrink-0 text-muted-foreground">{t(`audit.detailKeys.${key}`, { defaultValue: humanize(key) })}:</dt>
                <dd className="break-all">{t(`audit.detailValues.${value}`, { defaultValue: value })}</dd>
              </div>
            ))}
          </dl>
        ) : null}
      </td>
    </tr>
  );
}

const LOCAL_IPS = new Set(['::1', '127.0.0.1', '0:0:0:0:0:0:0:1']);

/** `ADMIN_LOGIN_FAILED` → "Admin login failed" — tərcüməsi olmayan (yeni) əməliyyat üçün oxunaqlı ehtiyat variant. */
function humanize(code: string): string {
  const words = code.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

type Translate = (key: string, options: { defaultValue: string }) => string;

/** Əməliyyatın 3 dildə adı; tərcüməsi yoxdursa — koddan düzəldilmiş mətn. */
export function actionLabel(t: Translate, action: string): string {
  return t(`audit.actions.${action}`, { defaultValue: humanize(action) });
}

/** Rəng əməliyyatın mənasına görə: uğursuz/bloklanmış — qırmızı, sıfırlama/söndürmə — sarı, yaradılma/uğur — yaşıl. */
function actionTone(action: string): 'danger' | 'warning' | 'success' | undefined {
  if (/(FAILED|BLOCKED|LOCKED|REJECTED|SUSPENDED|DELETED|REPLAY|LOOKUP_FAILED)$/.test(action) || action.includes('_LOCKED')) {
    return 'danger';
  }
  if (/(RESET|DISABLED|REMOVED|REVOKED|PAUSED|CANCELLED|EXPORTED|ALERT|ISSUED|REQUESTED|ROTATED)/.test(action)) {
    return 'warning';
  }
  if (/(SUCCEEDED|CREATED|ACTIVATED|APPROVED|ENABLED|PROVISIONED|REGISTERED|ACCEPTED|COMPLETED|REWARDED)$/.test(action)) {
    return 'success';
  }
  return undefined;
}

/** JSON təfərrüatı → [açar, dəyər] sətirləri; boş və ya oxunmayan JSON — null. */
function parseDetails(raw: string | undefined): [string, string][] | null {
  if (!raw || raw === '{}') {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const rows = Object.entries(parsed as Record<string, unknown>).map(
        ([key, value]): [string, string] => [key, typeof value === 'string' ? value : JSON.stringify(value)],
      );
      return rows.length > 0 ? rows : null;
    }
  } catch {
    // aşağıda xam mətn kimi
  }
  return [['details', raw]];
}
