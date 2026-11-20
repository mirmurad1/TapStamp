import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, Send } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { DEFAULT_TIME_ZONE, formatDateTime, formatNumber, formatPercent } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';

import { DEAL_NOTIFICATIONS_KEY, dealNotificationsApi, type DealNotificationOverview } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';

/** Avtomatik göndərişin saatı, yarım saatlıq addımla; müştəriyə isə filialının iş saatında çatır. */
const SEND_TIMES = Array.from({ length: 48 }, (_, index) => {
  const minutes = index * 30;
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${minutes % 60 === 0 ? '00' : '30'}`;
});

/** Şirkətin indiki saatı (HH:mm) — avtomatik göndərişin bu gün, yoxsa sabah olacağını demək üçün. */
function companyTime(): string {
  return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: DEFAULT_TIME_ZONE })
    .format(new Date());
}

type SendStatus = 'SENT' | 'NOTHING_TO_SEND' | 'ALREADY_SENT_TODAY' | 'UNAVAILABLE';

/**
 * Menyu səhifəsində "Endirim bildirişləri": yeni endirimlər bir bildirişdə — seçilmiş saatda avtomatik və ya "İndi
 * göndər" ilə. Elan gözləyən endirimlər, müştərinin görəcəyi bildiriş, parametrlər və son elanların nəticəsi.
 */
export function DealNotificationsCard() {
  const { t } = useTranslation('admin');
  const overview = useQuery({ queryKey: DEAL_NOTIFICATIONS_KEY, queryFn: dealNotificationsApi.get });
  const data = overview.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <BellRing className="size-5 text-primary" aria-hidden="true" />
          {t('catalog.deals.title')}
        </CardTitle>
        <CardDescription>{t('catalog.deals.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <ApiErrorAlert error={overview.error} />
        {data ? (
          <>
            <Pending data={data} />
            <SettingsForm data={data} />
            <Recent data={data} />
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

/** Elan gözləyən endirimlər, bildirişin önizləməsi və "İndi göndər". */
function Pending({ data }: { data: DealNotificationOverview }) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const [result, setResult] = useState<{ status: SendStatus; recipients: number; waiting: number }>();
  const send = useMutation({
    mutationFn: dealNotificationsApi.send,
    onSuccess: (response) => {
      setResult({
        status: (response.status ?? 'NOTHING_TO_SEND') as SendStatus,
        recipients: response.recipients ?? 0,
        waiting: response.waiting ?? 0,
      });
      void queryClient.invalidateQueries({ queryKey: DEAL_NOTIFICATIONS_KEY });
    },
  });
  const pending = data.pending ?? [];
  const blocked = !data.available
    ? 'unavailable'
    : data.sentToday
      ? 'sentToday'
      : null;
  const onSend = async () => {
    const ok = await confirm({
      message: t('admin:catalog.deals.confirm', { count: data.customersWithPush ?? 0 }),
      confirmLabel: t('admin:catalog.deals.send'),
    });
    if (ok) {
      send.mutate();
    }
  };
  return (
    <section className="flex flex-col gap-3">
      {result ? (
        <Alert tone={result.status === 'SENT' ? 'success' : 'info'}>
          {t(`admin:catalog.deals.result.${result.status}`, { count: result.recipients })}
          {result.status === 'SENT' && result.waiting > 0 ? ` ${t('admin:catalog.deals.result.waiting', { count: result.waiting })}` : null}
        </Alert>
      ) : null}
      <ApiErrorAlert error={send.error} />
      {pending.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('admin:catalog.deals.nothingPending')}</p>
      ) : (
        <>
          <p className="text-sm">
            <span className="font-semibold">{t('admin:catalog.deals.pending', { count: pending.length })}</span>{' '}
            <span className="text-muted-foreground">
              {pending.map((deal) => `${resolveText(deal.name, lang)} −${deal.percent ?? 0}%`).join(', ')}
            </span>
          </p>
          {data.preview ? (
            // Telefondakı bildiriş kimi: başlıq və mətn
            <div className="flex max-w-md items-start gap-3 rounded-2xl border border-border bg-muted/50 p-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground" aria-hidden="true">
                <BellRing className="size-4" />
              </span>
              <span className="min-w-0 text-sm">
                <span className="block text-xs text-muted-foreground">{t('admin:catalog.deals.preview')}</span>
                <span className="block font-semibold">{data.preview.title}</span>
                <span className="block text-muted-foreground">{data.preview.body}</span>
              </span>
            </div>
          ) : null}
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={() => void onSend()} disabled={send.isPending || blocked !== null}>
              <Send className="size-4" aria-hidden="true" />
              {t('admin:catalog.deals.send')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {blocked
                ? t(`admin:catalog.deals.blocked.${blocked}`)
                : data.settings?.autoEnabled
                  ? t(companyTime() < (data.settings.sendTime ?? '') ? 'admin:catalog.deals.autoAt' : 'admin:catalog.deals.autoTomorrow',
                    { time: data.settings.sendTime })
                  : t('admin:catalog.deals.manualOnly')}
            </span>
          </div>
        </>
      )}
      <p className="text-xs text-muted-foreground">
        {t('admin:catalog.deals.audience', { count: data.customersWithPush ?? 0, formatted: formatNumber(data.customersWithPush ?? 0, lang) })}
      </p>
    </section>
  );
}

function SettingsForm({ data }: { data: DealNotificationOverview }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [autoEnabled, setAutoEnabled] = useState(data.settings?.autoEnabled ?? true);
  const [sendTime, setSendTime] = useState(data.settings?.sendTime ?? '12:00');
  const [lastDay, setLastDay] = useState(data.settings?.lastDayEnabled ?? true);
  const [saved, setSaved] = useState(false);
  const save = useMutation({
    mutationFn: () => dealNotificationsApi.settings({ autoEnabled, sendTime, lastDayEnabled: lastDay }),
    onSuccess: (updated) => {
      queryClient.setQueryData(DEAL_NOTIFICATIONS_KEY, updated);
      setSaved(true);
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSaved(false);
    save.mutate();
  };
  const changed = autoEnabled !== data.settings?.autoEnabled || sendTime !== data.settings?.sendTime
    || lastDay !== data.settings?.lastDayEnabled;
  return (
    <form onSubmit={submit} className="flex flex-col gap-3 border-t border-border pt-4">
      <h3 className="text-sm font-semibold">{t('admin:catalog.deals.settings')}</h3>
      <ApiErrorAlert error={save.error} />
      {saved && !changed ? <Alert tone="success">{t('admin:catalog.deals.saved')}</Alert> : null}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-3">
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" checked={autoEnabled} onChange={(event) => setAutoEnabled(event.target.checked)}
            className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
          <span>
            <span className="block font-medium">{t('admin:catalog.deals.auto')}</span>
            <span className="block text-muted-foreground">{t('admin:catalog.deals.autoHint')}</span>
          </span>
        </label>
        <Field id="deal-send-time" label={t('admin:catalog.deals.time')} className="w-32">
          <Select value={sendTime} onChange={(event) => setSendTime(event.target.value)} disabled={!autoEnabled}>
            {SEND_TIMES.map((time) => (
              <option key={time} value={time}>
                {time}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <label className="flex items-start gap-3 text-sm">
        <input type="checkbox" checked={lastDay} onChange={(event) => setLastDay(event.target.checked)}
          className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
        <span>
          <span className="block font-medium">{t('admin:catalog.deals.lastDay')}</span>
          <span className="block text-muted-foreground">{t('admin:catalog.deals.lastDayHint')}</span>
        </span>
      </label>
      <ul className="list-disc space-y-0.5 pl-5 text-xs text-muted-foreground">
        <li>{t('admin:catalog.deals.rules.once')}</li>
        <li>{t('admin:catalog.deals.rules.daily')}</li>
        <li>{t('admin:catalog.deals.rules.branch')}</li>
      </ul>
      <Button type="submit" variant="outline" className="self-start" disabled={save.isPending || !changed}>
        {t('common:actions.save')}
      </Button>
    </form>
  );
}

/** Son elanlar: nə vaxt, növ, neçə müştəriyə getdi, neçəsinə çatdı və neçəsi açdı. */
function Recent({ data }: { data: DealNotificationOverview }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const recent = data.recent ?? [];
  return (
    <section className="flex flex-col gap-2 border-t border-border pt-4">
      <h3 className="text-sm font-semibold">{t('catalog.deals.recent')}</h3>
      {recent.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('catalog.deals.recentEmpty')}</p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border">
          {recent.map((row) => {
            const recipients = row.recipients ?? 0;
            const opened = row.opened ?? 0;
            return (
              <li key={row.id} className="flex flex-col gap-1 px-3 py-2.5 text-sm sm:flex-row sm:items-center sm:gap-3">
                <span className="flex flex-wrap items-center gap-2 sm:w-[23rem] sm:shrink-0">
                  <span className="tabular-nums">{row.createdAt ? formatDateTime(row.createdAt, lang) : ''}</span>
                  <Badge tone={row.kind === 'LAST_DAY' ? 'warning' : 'success'}>{t(`catalog.deals.kinds.${row.kind === 'LAST_DAY' ? 'LAST_DAY' : 'NEW'}`)}</Badge>
                  <Badge>{t(`catalog.deals.triggers.${row.trigger === 'MANUAL' ? 'MANUAL' : 'AUTO'}`)}</Badge>
                </span>
                <span className="text-muted-foreground">
                  {t('catalog.deals.stats', {
                    items: row.itemCount ?? 0,
                    recipients: formatNumber(recipients, lang),
                    delivered: formatNumber(row.delivered ?? 0, lang),
                    opened: formatNumber(opened, lang),
                    rate: formatPercent(recipients > 0 ? opened / recipients : 0, lang),
                  })}
                  {(row.waiting ?? 0) > 0 ? ` · ${t('catalog.deals.waiting', { count: row.waiting ?? 0 })}` : null}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
