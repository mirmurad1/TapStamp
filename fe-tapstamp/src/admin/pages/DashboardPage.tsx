import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Gift, Repeat, Stamp, UserPlus, Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import type { Language } from '@/shared/i18n/languages';
import { currentLanguage } from '@/shared/i18n/setup';
import { DEFAULT_TIME_ZONE, formatDate, formatNumber, formatPercent } from '@/shared/lib/format';
import { useFeature } from '@/shared/plan/plan';
import { Button } from '@/shared/ui/button';
import { Tabs } from '@/shared/ui/tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Select } from '@/shared/ui/input';

import {
  analyticsApi,
  type AnalyticsFilter,
  type HeatCell,
  type LanguageShare,
  type SeriesPoint,
} from '../api/adminApi';
import { useMe } from '../auth/AdminAuthProvider';
import { ApiErrorAlert } from '../components/ApiErrorAlert';
import { InlineLoading } from '../components/FullPageState';
import { PlanLockedNotice } from '../components/PlanLocked';
import { hasAnyRole } from '../layout/navigation';
import { useBranchNames } from '../lib/branches';
import { ComparisonView } from './dashboard/ComparisonView';
import { ReferralDashboard } from './referrals/ReferralDashboard';

const PERIODS = [7, 30, 90, 365] as const;
const VIEWS = ['overview', 'comparison'] as const;

/** Şirkətin "bu gün"ü (YYYY-MM-DD) — analitika tarixləri şirkətin saat qurşağındadır. */
function companyToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: DEFAULT_TIME_ZONE }).format(new Date());
}

/** Qrafik oxu üçün qısa tarix: GG.AA (bütün dillərdə eyni). */
function shortDate(isoDate: string | undefined): string {
  return isoDate ? `${isoDate.slice(8, 10)}.${isoDate.slice(5, 7)}` : '';
}

function shiftDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function DashboardPage() {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const canSeeAnalytics = hasAnyRole(me, ['COMPANY_OWNER', 'BRANCH_MANAGER']);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('admin:dashboard.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('admin:dashboard.subtitle')}</p>
      </div>
      {canSeeAnalytics ? <Analytics /> : <StaffShortcut />}
    </div>
  );
}

function StaffShortcut() {
  const { t } = useTranslation('admin');
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('dashboard.staff.title')}</CardTitle>
        <CardDescription>{t('dashboard.staff.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link to="/staff-console">
            {t('nav.staffConsole')}
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function Analytics() {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const lang = currentLanguage();
  const { branches } = useBranchNames();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  // Paketə görə: Basic-də yalnız əsas göstəricilər; filial seçimi (sahib üçün) və dəvət statistikası Premium-da
  const fullAnalytics = useFeature('FULL_ANALYTICS') === true;
  const branchComparison = useFeature('BRANCH_COMPARISON') === true;
  const referralStats = useFeature('REFERRAL_STATS') === true;
  const ownBranches = new Set((me.roles ?? []).map((grant) => grant.branchId).filter(Boolean));
  const branchOptions = isOwner ? branches : branches.filter((branch) => ownBranches.has(branch.id));

  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  // Görünüş: ümumi göstəricilər və ya filialların və işçilərin müqayisəsi
  const [view, setView] = useState<(typeof VIEWS)[number]>('overview');
  const [branchId, setBranchId] = useState('');
  const to = companyToday();
  const filter: AnalyticsFilter = { from: shiftDays(to, -(days - 1)), to, branchId: branchId || undefined };
  const granularity = days > 90 ? 'WEEK' : 'DAY';

  const overview = useQuery({ queryKey: ['analytics', 'overview', filter], queryFn: () => analyticsApi.overview(filter) });
  const series = useQuery({
    queryKey: ['analytics', 'series', filter, granularity],
    queryFn: () => analyticsApi.series({ ...filter, granularity }),
    enabled: fullAnalytics,
  });
  const heatmap = useQuery({ queryKey: ['analytics', 'heatmap', filter], queryFn: () => analyticsApi.heatmap(filter), enabled: fullAnalytics });
  const retention = useQuery({ queryKey: ['analytics', 'retention', filter], queryFn: () => analyticsApi.retention(filter), enabled: fullAnalytics });
  const languages = useQuery({ queryKey: ['analytics', 'languages', filter.branchId], queryFn: () => analyticsApi.languages(filter), enabled: fullAnalytics });

  const o = overview.data;
  const n = (value: number | undefined) => formatNumber(value ?? 0, lang);
  const error = overview.error ?? series.error ?? heatmap.error ?? retention.error ?? languages.error;

  return (
    <>
      {branchComparison ? (
        <Tabs value={view} onChange={setView} label={t('admin:dashboard.comparison.views')}
          options={VIEWS.map((value) => ({ value, label: t(`admin:dashboard.comparison.tabs.${value}`) }))} />
      ) : null}
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t('admin:dashboard.filters.period')}
          <Select value={days} onChange={(event) => setDays(Number(event.target.value) as (typeof PERIODS)[number])}>
            {PERIODS.map((value) => (
              <option key={value} value={value}>
                {t('admin:dashboard.filters.lastDays', { count: value })}
              </option>
            ))}
          </Select>
        </label>
        {view === 'overview' && (isOwner ? branchComparison : branchOptions.length > 1) ? (
          <label className="flex flex-col gap-1 text-sm font-medium">
            {t('admin:dashboard.filters.branch')}
            <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
              <option value="">{t('admin:dashboard.filters.allBranches')}</option>
              {branchOptions.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </Select>
          </label>
        ) : null}
        <p className="pb-2 text-sm text-muted-foreground">
          {formatDate(filter.from ?? to, lang)} — {formatDate(to, lang)}
        </p>
      </div>

      {view === 'comparison' ? <ComparisonView from={filter.from ?? to} to={to} /> : (
      <>
      <ApiErrorAlert error={error} />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-[repeat(auto-fit,minmax(11rem,1fr))]">
        <Kpi icon={<Users />} label={t('admin:dashboard.kpi.customers')} value={n(o?.totalCustomers)} loading={!o}>
          {t('admin:dashboard.kpi.active', { d30: n(o?.active30) })}
        </Kpi>
        <Kpi icon={<UserPlus />} label={t('admin:dashboard.kpi.newCustomers')} value={n(o?.newCustomersInPeriod)} loading={!o}>
          {t('admin:dashboard.kpi.newDetail', { week: n(o?.newThisWeek), month: n(o?.newThisMonth) })}
        </Kpi>
        <Kpi icon={<Stamp />} label={t('admin:dashboard.kpi.stamps')} value={n(o?.stampsInPeriod)} loading={!o}>
          {t('admin:dashboard.kpi.stampsDetail')}
        </Kpi>
        <Kpi icon={<Gift />} label={t('admin:dashboard.kpi.rewards')} value={n(o?.rewardsRedeemed)} loading={!o}>
          {t('admin:dashboard.kpi.rewardsDetail', {
            earned: n(o?.rewardsEarned),
            rate: o?.redemptionRate == null ? '—' : formatPercent(o.redemptionRate, lang),
          })}
        </Kpi>
        {fullAnalytics ? (
          <Kpi
            icon={<Repeat />}
            label={t('admin:dashboard.kpi.retention')}
            value={retention.data?.rate == null ? '—' : formatPercent(retention.data.rate, lang)}
            loading={!retention.data}
          >
            {t('admin:dashboard.kpi.retentionDetail', { returned: n(retention.data?.returned), cohort: n(retention.data?.cohort) })}
          </Kpi>
        ) : null}
      </div>

      {fullAnalytics ? (
        <>
          <Card>
            <CardHeader>
              <CardTitle>{t('admin:dashboard.series.title')}</CardTitle>
              <CardDescription>
                {granularity === 'WEEK' ? t('admin:dashboard.series.weekly') : t('admin:dashboard.series.daily')}
              </CardDescription>
            </CardHeader>
            <CardContent>{series.data ? <SeriesChart points={series.data} /> : <InlineLoading />}</CardContent>
          </Card>

          <div className="grid gap-4 xl:grid-cols-3">
            <Card className="min-w-0 xl:col-span-2">
              <CardHeader>
                <CardTitle>{t('admin:dashboard.heatmap.title')}</CardTitle>
                <CardDescription>{t('admin:dashboard.heatmap.description')}</CardDescription>
              </CardHeader>
              <CardContent>{heatmap.data ? <Heatmap cells={heatmap.data} /> : <InlineLoading />}</CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{t('admin:dashboard.languages.title')}</CardTitle>
              </CardHeader>
              <CardContent>{languages.data ? <Languages shares={languages.data} /> : <InlineLoading />}</CardContent>
            </Card>
          </div>
        </>
      ) : (
        <PlanLockedNotice feature="FULL_ANALYTICS" />
      )}

      {isOwner && referralStats ? <ReferralDashboard filter={filter} /> : null}
      </>
      )}
    </>
  );
}

function Kpi({
  icon,
  label,
  value,
  loading,
  children,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  loading: boolean;
  children: ReactNode;
}) {
  return (
    // Yığcam, şaquli kart: başlıq və kiçik ikon, böyük rəqəm, bir sətirlik izah — beşi bir sıraya sığır
    <div className="flex flex-col gap-1 rounded-2xl border border-border bg-card p-4">
        <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
          <span className="truncate">{label}</span>
          <span className="shrink-0 text-primary [&>svg]:size-4" aria-hidden="true">
            {icon}
          </span>
        </div>
        <div>
          <p className="font-display text-3xl font-semibold tabular-nums" aria-busy={loading}>
            {loading ? '…' : value}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">{loading ? ' ' : children}</p>
        </div>
    </div>
  );
}

/** Möhürlər (sütun) və təhvil verilən hədiyyələr (xətt) — kitabxanasız SVG qrafik. */
function SeriesChart({ points }: { points: SeriesPoint[] }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const width = 720;
  const height = 220;
  const pad = { left: 36, right: 8, top: 12, bottom: 28 };
  const max = Math.max(1, ...points.map((p) => Math.max(p.stamps ?? 0, p.rewardsRedeemed ?? 0)));
  const step = (width - pad.left - pad.right) / Math.max(points.length, 1);
  const y = (value: number) => pad.top + (height - pad.top - pad.bottom) * (1 - value / max);
  const x = (index: number) => pad.left + step * index + step / 2;
  const ticks = [0, Math.round(max / 2), max];
  const labelEvery = Math.ceil(points.length / 6);
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.rewardsRedeemed ?? 0).toFixed(1)}`).join(' ');

  return (
    <div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full" role="img" aria-label={t('dashboard.series.title')}>
        {ticks.map((tick) => (
          <g key={tick}>
            <line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} className="stroke-border" strokeDasharray="3 3" />
            <text x={pad.left - 6} y={y(tick) + 4} textAnchor="end" className="fill-muted-foreground text-[10px]">
              {tick}
            </text>
          </g>
        ))}
        {points.map((p, i) => {
          const value = p.stamps ?? 0;
          return (
            <rect
              key={p.date}
              x={x(i) - Math.max(step * 0.32, 1)}
              width={Math.max(step * 0.64, 2)}
              y={y(value)}
              height={Math.max(y(0) - y(value), 0)}
              rx={2}
              className="fill-primary/80"
            >
              <title>{`${formatDate(p.date ?? '', lang)}: ${t('dashboard.series.tooltip', { stamps: value, rewards: p.rewardsRedeemed ?? 0 })}`}</title>
            </rect>
          );
        })}
        <path d={line} fill="none" className="stroke-accent" strokeWidth={2} strokeLinejoin="round" />
        {points.map((p, i) =>
          i % labelEvery === 0 ? (
            <text key={p.date} x={x(i)} y={height - 8} textAnchor="middle" className="fill-muted-foreground text-[10px]">
              {shortDate(p.date)}
            </text>
          ) : null,
        )}
      </svg>
      <div className="mt-2 flex flex-wrap gap-4 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="size-3 rounded-sm bg-primary/80" aria-hidden="true" />
          {t('dashboard.series.stamps')}
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-0.5 w-4 bg-accent" aria-hidden="true" />
          {t('dashboard.series.rewards')}
        </span>
      </div>
    </div>
  );
}

/** Həftənin günü × saat: rəngin tündlüyü möhür sayına mütənasibdir. */
function Heatmap({ cells }: { cells: HeatCell[] }) {
  const { t } = useTranslation('admin');
  const counts = new Map(cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell.stamps ?? 0]));
  const max = Math.max(1, ...cells.map((cell) => cell.stamps ?? 0));
  const days = [1, 2, 3, 4, 5, 6, 7] as const;
  const hours = Array.from({ length: 24 }, (_, hour) => hour);

  if (cells.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('dashboard.empty')}</p>;
  }
  return (
    <div className="overflow-x-auto" tabIndex={0}>
      <table className="w-full min-w-[36rem] table-fixed border-separate border-spacing-0.5 text-[10px]">
        <thead>
          <tr>
            <th scope="col" className="w-10" />
            {hours.map((hour) => (
              <th key={hour} scope="col" className="font-normal text-muted-foreground">
                {hour % 3 === 0 ? hour : ''}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {days.map((day) => (
            <tr key={day}>
              <th scope="row" className="pe-2 text-left font-medium text-muted-foreground">
                {t(`dashboard.heatmap.days.${day}`)}
              </th>
              {hours.map((hour) => {
                const count = counts.get(`${day}-${hour}`) ?? 0;
                return (
                  <td
                    key={hour}
                    className="h-5 rounded-sm"
                    style={{
                      backgroundColor:
                        count === 0 ? 'var(--muted)' : `color-mix(in oklab, var(--primary) ${Math.round(20 + (count / max) * 80)}%, transparent)`,
                    }}
                    title={t('dashboard.heatmap.cell', { day: t(`dashboard.heatmap.days.${day}`), hour, count })}
                  />
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Languages({ shares }: { shares: LanguageShare[] }) {
  const { t } = useTranslation('common');
  const lang = currentLanguage();
  const total = shares.reduce((sum, share) => sum + (share.customers ?? 0), 0);
  return (
    <ul className="flex flex-col gap-4">
      {shares.map((share) => {
        const ratio = total === 0 ? 0 : (share.customers ?? 0) / total;
        return (
          <li key={share.language}>
            <div className="mb-1 flex justify-between text-sm">
              <span className="font-medium">{t(`language.${share.language as Language}`)}</span>
              <span className="tabular-nums text-muted-foreground">
                {formatNumber(share.customers ?? 0, lang)} · {formatPercent(ratio, lang)}
              </span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-linear-to-r from-primary to-accent" style={{ width: `${ratio * 100}%` }} />
            </div>
          </li>
        );
      })}
    </ul>
  );
}
