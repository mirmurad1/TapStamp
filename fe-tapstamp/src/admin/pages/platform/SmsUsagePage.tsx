import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber } from '@/shared/lib/format';
import { Card, CardContent } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { platformSmsApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

const PERIODS = ['thisMonth', 'lastMonth', 'last7', 'last30', 'thisYear', 'custom'] as const;
type Period = (typeof PERIODS)[number];
const TOTALS = ['messages', 'segments', 'failed'] as const;

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
function periodRange(period: Exclude<Period, 'custom'>, today: string): { from: string; to: string } {
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

/** SMS hesabatı: hansı şirkət seçilmiş dövrdə nə qədər SMS istifadə edib. */
export function SmsUsagePage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const today = bakuToday();
  const [period, setPeriod] = useState<Period>('thisMonth');
  const [custom, setCustom] = useState(() => periodRange('thisMonth', today));
  const range = period === 'custom' ? custom : periodRange(period, today);
  const valid = Boolean(range.from && range.to && range.from <= range.to);

  const report = useQuery({
    queryKey: ['platform-sms-usage', range.from, range.to],
    queryFn: () => platformSmsApi.usage(range.from, range.to),
    enabled: valid,
  });
  const rows = report.data?.tenants ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('platform.sms.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('platform.sms.subtitle')}</p>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <Field id="sms-period" label={t('platform.sms.period')}>
          <Select value={period} onChange={(event) => setPeriod(event.target.value as Period)}>
            {PERIODS.map((value) => (
              <option key={value} value={value}>
                {t(`platform.sms.periods.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
        {period === 'custom' ? (
          <>
            <Field id="sms-from" label={t('platform.sms.from')}>
              <Input type="date" value={custom.from} max={today} onChange={(e) => setCustom({ ...custom, from: e.target.value })} />
            </Field>
            <Field id="sms-to" label={t('platform.sms.to')}>
              <Input type="date" value={custom.to} max={today} onChange={(e) => setCustom({ ...custom, to: e.target.value })} />
            </Field>
          </>
        ) : null}
      </div>

      {!valid ? <p className="text-sm text-destructive">{t('platform.sms.invalidRange')}</p> : null}

      {report.data ? (
        <div className="grid gap-4 sm:grid-cols-3">
          {TOTALS.map((key) => (
            <Card key={key}>
              <CardContent className="pt-5 sm:pt-6">
                <p className="text-sm text-muted-foreground">{t(`platform.sms.columns.${key}`)}</p>
                <p className="mt-1 font-display text-2xl font-semibold">{formatNumber(report.data[key] ?? 0, lang)}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : null}

      <Card>
        <CardContent className="flex flex-col gap-3 pt-5 sm:pt-6">
          <ApiErrorAlert error={report.error} />
          {report.isFetching && !report.data ? <InlineLoading /> : null}
          {report.data && rows.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{t('platform.sms.empty')}</p>
          ) : null}
          {rows.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th className="py-2 pr-4 font-medium">{t('platform.sms.columns.company')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('platform.sms.columns.messages')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('platform.sms.columns.segments')}</th>
                    <th className="py-2 text-right font-medium">{t('platform.sms.columns.failed')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {rows.map((row) => (
                    <tr key={row.tenantId}>
                      <td className="py-2 pr-4">
                        <span className="font-medium">{resolveText(row.displayName, lang)}</span>
                        <span className="ml-2 text-muted-foreground">{row.slug}</span>
                      </td>
                      <td className="py-2 pr-4 text-right tabular-nums">{formatNumber(row.messages ?? 0, lang)}</td>
                      <td className="py-2 pr-4 text-right tabular-nums">{formatNumber(row.segments ?? 0, lang)}</td>
                      <td className="py-2 text-right tabular-nums">{formatNumber(row.failed ?? 0, lang)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          <p className="text-xs text-muted-foreground">{t('platform.sms.note')}</p>
        </CardContent>
      </Card>
    </div>
  );
}
