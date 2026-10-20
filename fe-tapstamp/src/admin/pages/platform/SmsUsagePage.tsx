import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDateTime, formatNumber } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformSmsApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { toCsv } from '../../lib/csv';
import { PlatformSmsBudgetCard } from './SmsLimitCards';
import { downloadBlob } from '../../lib/download';

const PERIODS = ['thisMonth', 'lastMonth', 'last7', 'last30', 'thisYear', 'custom'] as const;
type Period = (typeof PERIODS)[number];
type Range = { from: string; to: string };

/** Bakı vaxtı ilə bu gün (YYYY-MM-DD) — backend də günləri Bakı vaxtı ilə sayır. */
function bakuToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Baku' }).format(new Date());
}

function shiftDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Seçilmiş dövrün günləri (hər ikisi daxil). */
function periodRange(period: Exclude<Period, 'custom'>, today: string): Range {
  const [year, month] = today.split('-');
  switch (period) {
    case 'thisMonth':
      return { from: `${year}-${month}-01`, to: today };
    case 'lastMonth': {
      const firstOfThisMonth = `${year}-${month}-01`;
      const lastOfPrevious = shiftDays(firstOfThisMonth, -1);
      return { from: `${lastOfPrevious.slice(0, 7)}-01`, to: lastOfPrevious };
    }
    case 'last7':
      return { from: shiftDays(today, -6), to: today };
    case 'last30':
      return { from: shiftDays(today, -29), to: today };
    case 'thisYear':
      return { from: `${year}-01-01`, to: today };
  }
}

/** Dövr seçimi (hazır dövrlər və ya tarix aralığı); seçilmiş aralığı və onun düzgünlüyünü qaytarır. */
function usePeriod(today: string) {
  const [period, setPeriod] = useState<Period>('thisMonth');
  const [custom, setCustom] = useState(() => periodRange('thisMonth', today));
  const range = period === 'custom' ? custom : periodRange(period, today);
  const valid = Boolean(range.from && range.to && range.from <= range.to);
  return { period, setPeriod, custom, setCustom, range, valid };
}

function PeriodPicker({ id, today, state }: { id: string; today: string; state: ReturnType<typeof usePeriod> }) {
  const { t } = useTranslation('admin');
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-end gap-4">
        <Field id={`${id}-period`} label={t('platform.sms.period')}>
          <Select value={state.period} onChange={(event) => state.setPeriod(event.target.value as Period)}>
            {PERIODS.map((value) => (
              <option key={value} value={value}>
                {t(`platform.sms.periods.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
        {state.period === 'custom' ? (
          <>
            <Field id={`${id}-from`} label={t('platform.sms.from')}>
              <Input type="date" value={state.custom.from} max={today}
                     onChange={(e) => state.setCustom({ ...state.custom, from: e.target.value })} />
            </Field>
            <Field id={`${id}-to`} label={t('platform.sms.to')}>
              <Input type="date" value={state.custom.to} max={today}
                     onChange={(e) => state.setCustom({ ...state.custom, to: e.target.value })} />
            </Field>
          </>
        ) : null}
      </div>
      {!state.valid ? <p className="text-sm text-destructive">{t('platform.sms.invalidRange')}</p> : null}
    </div>
  );
}

/** SMS hesabatı: şirkətlər üzrə uğurlu SMS-lər və ayrıca cədvəldə göndərilə bilməyənlər (səbəbi ilə). */
export function SmsUsagePage() {
  const { t } = useTranslation('admin');
  const today = bakuToday();

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('platform.sms.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('platform.sms.subtitle')}</p>
      </div>
      <PlatformSmsBudgetCard />
      <UsageSection today={today} />
      <MessagesSection today={today} mode="failures" />
      <MessagesSection today={today} mode="search" />
    </div>
  );
}

function UsageSection({ today }: { today: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const period = usePeriod(today);
  const { range, valid } = period;
  const report = useQuery({
    queryKey: ['platform-sms-usage', range.from, range.to],
    queryFn: () => platformSmsApi.usage(range.from, range.to),
    enabled: valid,
  });
  const rows = report.data?.tenants ?? [];
  // Hesabat bütöv gəlir (ixrac üçün); cədvəldə hissə-hissə göstərilir
  const rowPaging = usePagination(report.data);
  const visibleRows = rows.slice(rowPaging.page * rowPaging.size, (rowPaging.page + 1) * rowPaging.size);

  /** Excel üçün CSV: şirkət, kod, uğurlu SMS sayı və cəm sətri. */
  const exportCsv = () => {
    const header = [t('platform.sms.columns.company'), t('platform.sms.columns.slug'), t('platform.sms.columns.otp'),
      t('platform.sms.columns.campaign'), t('platform.sms.columns.messages')];
    const lines = rows.map((row) => [resolveText(row.displayName, lang), row.slug ?? '', row.otp ?? 0, row.campaign ?? 0,
      row.messages ?? 0]);
    const total = [t('platform.sms.export.total'), '', report.data?.otp ?? 0, report.data?.campaign ?? 0,
      report.data?.messages ?? 0];
    downloadBlob(toCsv([header, ...lines, total]), `sms-${range.from}_${range.to}.csv`);
  };

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
        <CardTitle>{t('platform.sms.usageTitle')}</CardTitle>
        <Button variant="outline" onClick={exportCsv} disabled={rows.length === 0}>
          <Download className="size-4" aria-hidden="true" />
          {t('platform.sms.export.button')}
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <PeriodPicker id="sms-usage" today={today} state={period} />
        <ApiErrorAlert error={report.error} />
        {report.isFetching && !report.data ? <InlineLoading /> : null}
        {report.data ? (
          <div>
            <p className="text-sm text-muted-foreground">{t('platform.sms.total')}</p>
            <p className="mt-1 font-display text-2xl font-semibold">{formatNumber(report.data.messages ?? 0, lang)}</p>
          </div>
        ) : null}
        {report.data && rows.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('platform.sms.empty')}</p>
        ) : null}
        {rows.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('platform.sms.columns.company')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('platform.sms.columns.otp')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('platform.sms.columns.campaign')}</th>
                  <th className="py-2 text-right font-medium">{t('platform.sms.columns.messages')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {visibleRows.map((row) => (
                  <tr key={row.tenantId}>
                    <td className="py-2 pr-4">
                      <span className="font-medium">{resolveText(row.displayName, lang)}</span>
                      <span className="ml-2 text-muted-foreground">{row.slug}</span>
                    </td>
                    <td className="py-2 pr-4 text-right tabular-nums">{formatNumber(row.otp ?? 0, lang)}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">{formatNumber(row.campaign ?? 0, lang)}</td>
                    <td className="py-2 text-right font-medium tabular-nums">{formatNumber(row.messages ?? 0, lang)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        <Pagination state={rowPaging} total={rows.length} />
        <p className="text-xs text-muted-foreground">{t('platform.sms.note')}</p>
      </CardContent>
    </Card>
  );
}

type MessagesMode = 'failures' | 'search';
const STATUSES = ['', 'SENT', 'FAILED'] as const;
type StatusFilter = (typeof STATUSES)[number];

/**
 * SMS siyahısı səhifə-səhifə: "failures" — yalnız göndərilə bilməyənlər (səbəbi ilə), "search" — nömrəyə və statusa
 * görə axtarış (hansı nömrəyə hansı mesaj getdi).
 */
function MessagesSection({ today, mode }: { today: string; mode: MessagesMode }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const period = usePeriod(today);
  const { range, valid } = period;
  const [phoneInput, setPhoneInput] = useState('');
  const [phone, setPhone] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('');
  const status = mode === 'failures' ? 'FAILED' : statusFilter || undefined;
  // Filtr dəyişəndə birinci səhifəyə qayıdırıq
  const paging = usePagination([range.from, range.to, phone, status]);
  const { page, size } = paging;
  const messages = useQuery({
    queryKey: ['platform-sms-messages', range.from, range.to, status, phone, page, size],
    queryFn: () => platformSmsApi.messages({ from: range.from, to: range.to, status, phone: phone || undefined, page, size }),
    enabled: valid,
    placeholderData: keepPreviousData,
  });
  const items = messages.data?.items ?? [];
  const total = messages.data?.total ?? 0;
  const prefix = mode === 'failures' ? 'sms-failures' : 'sms-search';

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t(`platform.sms.${mode}.title`)}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <PeriodPicker id={prefix} today={today} state={period} />
        {mode === 'search' ? (
          <form
            className="flex flex-col gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setPhone(phoneInput.trim());
            }}
          >
            {/* Sahələr və düymə bir sırada; nömrənin izahı altda (sahənin içində olsaydı, sıranı pozurdu) */}
            <div className="flex flex-wrap items-end gap-4">
              <Field id={`${prefix}-phone`} label={t('platform.sms.search.phone')}>
                <Input type="search" inputMode="tel" value={phoneInput} maxLength={32}
                       onChange={(event) => setPhoneInput(event.target.value)} />
              </Field>
              <Field id={`${prefix}-status`} label={t('platform.sms.search.status')}>
                <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
                  {STATUSES.map((value) => (
                    <option key={value || 'all'} value={value}>
                      {t(`platform.sms.statuses.${value || 'ALL'}`)}
                    </option>
                  ))}
                </Select>
              </Field>
              <Button type="submit">{t('platform.sms.search.submit')}</Button>
            </div>
            <p className="text-xs text-muted-foreground">{t('platform.sms.search.phoneHint')}</p>
          </form>
        ) : null}
        <ApiErrorAlert error={messages.error} />
        {messages.isFetching && !messages.data ? <InlineLoading /> : null}
        {messages.data ? (
          <p className="text-sm text-muted-foreground">{t(`platform.sms.${mode}.count`, { count: total })}</p>
        ) : null}
        {messages.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t(`platform.sms.${mode}.empty`)}</p>
        ) : null}
        {items.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('platform.sms.failures.time')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.sms.columns.company')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.sms.failures.phone')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.sms.failures.text')}</th>
                  {mode === 'search' ? <th className="py-2 pr-4 font-medium">{t('platform.sms.search.status')}</th> : null}
                  <th className="py-2 font-medium">{t('platform.sms.failures.error')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {items.map((item) => (
                  <tr key={item.id} className="align-top">
                    <td className="whitespace-nowrap py-2 pr-4 tabular-nums">
                      {item.createdAt ? formatDateTime(item.createdAt, lang) : null}
                    </td>
                    <td className="py-2 pr-4">{resolveText(item.displayName, lang)}</td>
                    <td className="whitespace-nowrap py-2 pr-4 tabular-nums">{item.phone}</td>
                    <td className="py-2 pr-4">
                      {item.purpose === 'CAMPAIGN' ? (
                        <Badge className="mb-1">{t('platform.sms.purposes.CAMPAIGN')}</Badge>
                      ) : null}
                      <p>{item.text}</p>
                    </td>
                    {mode === 'search' ? (
                      <td className="whitespace-nowrap py-2 pr-4">
                        <Badge tone={item.status === 'SENT' ? 'success' : 'danger'}>
                          {t(`platform.sms.statuses.${item.status ?? 'SENT'}`)}
                        </Badge>
                      </td>
                    ) : null}
                    <td className="break-all py-2 text-destructive">{item.error}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        <Pagination state={paging} total={total} />
      </CardContent>
    </Card>
  );
}