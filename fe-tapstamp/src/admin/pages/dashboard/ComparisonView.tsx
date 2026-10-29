import { useQuery } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, Download } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatNumber, formatPercent } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { analyticsApi, type BranchComparison, type StaffComparison } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { useBranchNames } from '../../lib/branches';

type BranchRow = NonNullable<BranchComparison['current']>;

/** Məmnuniyyət: əla qiymətlərin payı; rəy yoxdursa — boş. */
function satisfaction(row: BranchRow | undefined): number | undefined {
  const total = (row?.good ?? 0) + (row?.ok ?? 0) + (row?.bad ?? 0);
  return total === 0 ? undefined : (row?.good ?? 0) / total;
}

interface Column<T> {
  key: string;
  value: (row: T) => number | undefined;
  /** Kiçik daha yaxşıdır (məs. pis rəylər) — rəngləmə tərsinə. */
  lowerIsBetter?: boolean;
  percent?: boolean;
}

const BRANCH_COLUMNS: Column<BranchComparison>[] = [
  { key: 'visits', value: (r) => r.current?.visits },
  { key: 'customers', value: (r) => r.current?.customers },
  { key: 'newCustomers', value: (r) => r.current?.newCustomers },
  { key: 'returning', value: (r) => r.current?.returning },
  { key: 'rewardsEarned', value: (r) => r.current?.rewardsEarned },
  { key: 'rewardsRedeemed', value: (r) => r.current?.rewardsRedeemed },
  { key: 'bonusStamps', value: (r) => r.current?.bonusStamps },
  { key: 'satisfaction', value: (r) => satisfaction(r.current), percent: true },
];

const PREVIOUS: Record<string, (r: BranchComparison) => number | undefined> = {
  visits: (r) => r.previous?.visits,
  customers: (r) => r.previous?.customers,
  newCustomers: (r) => r.previous?.newCustomers,
  returning: (r) => r.previous?.returning,
  rewardsEarned: (r) => r.previous?.rewardsEarned,
  rewardsRedeemed: (r) => r.previous?.rewardsRedeemed,
  bonusStamps: (r) => r.previous?.bonusStamps,
  satisfaction: (r) => satisfaction(r.previous),
};

const STAFF_COLUMNS: Column<StaffComparison>[] = [
  { key: 'confirmed', value: (r) => r.confirmed },
  { key: 'rejected', value: (r) => r.rejected },
  { key: 'manual', value: (r) => r.manual },
  { key: 'redeemed', value: (r) => r.redeemed },
];

/** CSV faylı (Excel üçün BOM ilə) — brauzerdə yaradılır, serverə sorğu getmir. */
function downloadCsv(name: string, rows: (string | number)[][]) {
  const escape = (cell: string | number) => `"${String(cell).replaceAll('"', '""')}"`;
  const text = rows.map((row) => row.map(escape).join(',')).join('\n');
  const url = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

/** Sıralama: sütuna klik — azalan, ikinci klik — artan. */
function useSort<T>(columns: Column<T>[], initial: string) {
  const [sort, setSort] = useState({ key: initial, desc: true });
  const toggle = (key: string) => setSort((s) => (s.key === key ? { key, desc: !s.desc } : { key, desc: true }));
  const apply = (rows: T[]) => {
    const column = columns.find((c) => c.key === sort.key);
    if (!column) {
      return rows;
    }
    return [...rows].sort((a, b) => {
      const diff = (column.value(a) ?? -1) - (column.value(b) ?? -1);
      return sort.desc ? -diff : diff;
    });
  };
  return { sort, toggle, apply };
}

/** Sütunda ən yaxşı və ən zəif dəyər (ən azı iki fərqli dəyər olanda). */
function extremes<T>(rows: T[], column: Column<T>) {
  const values = rows.map(column.value).filter((v): v is number => v !== undefined);
  if (values.length < 2 || Math.min(...values) === Math.max(...values)) {
    return { best: undefined, worst: undefined };
  }
  const max = Math.max(...values);
  const min = Math.min(...values);
  return column.lowerIsBetter ? { best: min, worst: max } : { best: max, worst: min };
}

function SortHeader({ label, active, desc, onClick, className }: {
  label: string;
  active: boolean;
  desc: boolean;
  onClick: () => void;
  className?: string;
}) {
  return (
    <th scope="col" className={cn('px-3 py-2 font-medium', className)} aria-sort={active ? (desc ? 'descending' : 'ascending') : 'none'}>
      <button type="button" onClick={onClick} className="inline-flex items-center gap-1 hover:text-foreground">
        {label}
        {active ? (desc ? <ArrowDown className="size-3" aria-hidden="true" /> : <ArrowUp className="size-3" aria-hidden="true" />) : null}
      </button>
    </th>
  );
}

/** Filialların və işçilərin müqayisəsi (Göstəricilər paneli → Müqayisə). */
export function ComparisonView({ from, to }: { from: string; to: string }) {
  const { t } = useTranslation('admin');
  const comparison = useQuery({
    queryKey: ['analytics', 'comparison', from, to],
    queryFn: () => analyticsApi.comparison({ from, to }),
  });
  return (
    <>
      <ApiErrorAlert error={comparison.error} />
      {comparison.isPending ? <InlineLoading /> : null}
      {comparison.data ? (
        <>
          <BranchTable rows={comparison.data.branches ?? []} />
          <StaffTable rows={comparison.data.staff ?? []} />
        </>
      ) : null}
      <p className="text-xs text-muted-foreground">{t('dashboard.comparison.note')}</p>
    </>
  );
}

function BranchTable({ rows }: { rows: BranchComparison[] }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const { sort, toggle, apply } = useSort(BRANCH_COLUMNS, 'visits');
  const sorted = apply(rows);
  const format = (column: Column<BranchComparison>, value: number | undefined) =>
    value === undefined ? '—' : column.percent ? formatPercent(value, lang, 0) : formatNumber(value, lang);
  const delta = (column: Column<BranchComparison>, row: BranchComparison): ReactNode => {
    const previous = PREVIOUS[column.key]?.(row);
    const current = column.value(row);
    if (previous === undefined || current === undefined || previous === current) {
      return null;
    }
    const up = current > previous;
    const text = column.percent
      ? t('dashboard.comparison.points', { count: Math.round(Math.abs(current - previous) * 100) })
      : previous === 0
        ? `+${formatNumber(current, lang)}`
        : formatPercent(Math.abs(current - previous) / previous, lang, 0);
    return (
      <span className={cn('block text-[11px]', up ? 'text-[var(--success-ink)]' : 'text-[var(--destructive-ink)]')}>
        {up ? '↑' : '↓'} {text}
      </span>
    );
  };
  const exportCsv = () =>
    downloadCsv(t('dashboard.comparison.branchesFile'), [
      [t('dashboard.comparison.branch'), ...BRANCH_COLUMNS.map((c) => t(`dashboard.comparison.columns.${c.key as 'visits'}`))],
      ...sorted.map((row) => [
        resolveText(row.current?.name, lang),
        ...BRANCH_COLUMNS.map((c) => format(c, c.value(row))),
      ]),
    ]);
  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>{t('dashboard.comparison.branchesTitle')}</CardTitle>
          <CardDescription>{t('dashboard.comparison.branchesHint')}</CardDescription>
        </div>
        <Button variant="outline" size="sm" onClick={exportCsv} disabled={rows.length === 0}>
          <Download className="size-4" aria-hidden="true" />
          {t('dashboard.comparison.csv')}
        </Button>
      </CardHeader>
      <CardContent className="-mx-5 overflow-x-auto px-0 sm:-mx-6">
        <table className="w-full min-w-[64rem] text-left text-sm">
          <thead className="border-b border-border text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('dashboard.comparison.branch')}</th>
              {BRANCH_COLUMNS.map((column) => (
                <SortHeader key={column.key} label={t(`dashboard.comparison.columns.${column.key as 'visits'}`)}
                  active={sort.key === column.key} desc={sort.desc} onClick={() => toggle(column.key)} className="text-right" />
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {sorted.map((row) => (
              <tr key={row.current?.branchId}>
                <th scope="row" className="px-5 py-2 font-medium sm:px-6">{resolveText(row.current?.name, lang)}</th>
                {BRANCH_COLUMNS.map((column) => {
                  const value = column.value(row);
                  const { best, worst } = extremes(rows, column);
                  return (
                    <td key={column.key} className={cn('px-3 py-2 text-right tabular-nums',
                      value !== undefined && value === best && 'bg-success/10 font-semibold text-[var(--success-ink)]',
                      value !== undefined && value === worst && 'bg-destructive/10 text-[var(--destructive-ink)]')}>
                      {format(column, value)}
                      {delta(column, row)}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}

function StaffTable({ rows }: { rows: StaffComparison[] }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const { nameOf } = useBranchNames();
  const { sort, toggle, apply } = useSort(STAFF_COLUMNS, 'confirmed');
  const sorted = apply(rows);
  const branchesOf = (row: StaffComparison) =>
    (row.branchIds ?? []).length === 0 ? t('dashboard.comparison.allBranches') : (row.branchIds ?? []).map((id) => nameOf(id) ?? '').join(', ');
  const exportCsv = () =>
    downloadCsv(t('dashboard.comparison.staffFile'), [
      [t('dashboard.comparison.staff'), t('dashboard.comparison.branch'), ...STAFF_COLUMNS.map((c) => t(`dashboard.comparison.staffColumns.${c.key as 'confirmed'}`))],
      ...sorted.map((row) => [row.name ?? '', branchesOf(row), ...STAFF_COLUMNS.map((c) => c.value(row) ?? 0)]),
    ]);
  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>{t('dashboard.comparison.staffTitle')}</CardTitle>
          <CardDescription>{t('dashboard.comparison.staffHint')}</CardDescription>
        </div>
        <Button variant="outline" size="sm" onClick={exportCsv} disabled={rows.length === 0}>
          <Download className="size-4" aria-hidden="true" />
          {t('dashboard.comparison.csv')}
        </Button>
      </CardHeader>
      <CardContent className="-mx-5 overflow-x-auto px-0 sm:-mx-6">
        <table className="w-full min-w-[40rem] text-left text-sm">
          <thead className="border-b border-border text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('dashboard.comparison.staff')}</th>
              <th scope="col" className="px-3 py-2 font-medium">{t('dashboard.comparison.branch')}</th>
              {STAFF_COLUMNS.map((column) => (
                <SortHeader key={column.key} label={t(`dashboard.comparison.staffColumns.${column.key as 'confirmed'}`)}
                  active={sort.key === column.key} desc={sort.desc} onClick={() => toggle(column.key)} className="text-right" />
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {sorted.map((row) => (
              <tr key={row.staffId}>
                <th scope="row" className="px-5 py-2 font-medium sm:px-6">{row.name}</th>
                <td className="px-3 py-2 text-muted-foreground">{branchesOf(row)}</td>
                {STAFF_COLUMNS.map((column) => (
                  <td key={column.key} className="px-3 py-2 text-right tabular-nums">{formatNumber(column.value(row) ?? 0, lang)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}
