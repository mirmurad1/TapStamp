import { useQuery } from '@tanstack/react-query';
import { ArrowRight, UserPlus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber, formatPercent } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { referralsApi, type AnalyticsFilter } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

/** Payın faizi; məxrəc 0-dırsa null (mətn əvəzinə "—" göstərilir). */
function share(part: number, whole: number, lang: ReturnType<typeof currentLanguage>): string | null {
  return whole > 0 ? formatPercent(part / whole, lang) : null;
}

/**
 * İdarəetmə panelində "Dost dəvəti" bloku (yalnız sahib): panelin dövr və filial filtrləri ilə. Proqram söndürülüb və
 * dövrdə dəvət yoxdursa, göstərilmir.
 */
export function ReferralDashboard({ filter }: { filter: AnalyticsFilter }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const settings = useQuery({ queryKey: ['referral-settings'], queryFn: referralsApi.settings });
  const dashboard = useQuery({ queryKey: ['referral-dashboard', filter], queryFn: () => referralsApi.dashboard(filter) });
  const stats = dashboard.data?.stats;
  const top = dashboard.data?.topReferrers ?? [];
  if (!settings.data?.enabled && !stats?.invited) {
    return null;
  }
  const n = (value: number | undefined) => formatNumber(value ?? 0, lang);
  const invited = stats?.invited ?? 0;
  const came = stats?.came ?? 0;
  const cameRate = share(came, invited, lang);
  const returnedRate = share(stats?.returned ?? 0, came, lang);
  const items = [
    { label: t('referrals.dashboard.invited'), value: n(invited), detail: t('referrals.dashboard.invitedHint') },
    { label: t('referrals.dashboard.came'), value: n(came), detail: cameRate ? t('referrals.dashboard.ofInvited', { rate: cameRate }) : '—' },
    {
      label: t('referrals.dashboard.returned'),
      value: n(stats?.returned),
      detail: returnedRate ? t('referrals.dashboard.ofCame', { rate: returnedRate }) : '—',
    },
    { label: t('referrals.dashboard.visits'), value: n(stats?.visits), detail: t('referrals.dashboard.visitsHint') },
    { label: t('referrals.dashboard.giftCards'), value: n(stats?.giftCards), detail: t('referrals.dashboard.giftCardsHint') },
    { label: t('referrals.dashboard.bonusStamps'), value: n(stats?.bonusStamps), detail: t('referrals.dashboard.bonusStampsHint') },
  ];

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle className="flex items-center gap-2">
            <UserPlus className="size-5 text-primary" aria-hidden="true" />
            {t('referrals.dashboard.title')}
          </CardTitle>
          <CardDescription>{t('referrals.dashboard.description')}</CardDescription>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link to="/referrals">
            {t('referrals.dashboard.details')}
            <ArrowRight aria-hidden="true" />
          </Link>
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <ApiErrorAlert error={dashboard.error ?? settings.error} />
        {dashboard.isPending ? <InlineLoading /> : null}
        {stats ? (
          <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((item) => (
              <div key={item.label} className="rounded-xl bg-muted px-4 py-3">
                <dt className="text-sm text-muted-foreground">{item.label}</dt>
                <dd className="mt-1 font-display text-2xl font-semibold tabular-nums">{item.value}</dd>
                <dd className="text-xs text-muted-foreground">{item.detail}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {top.length > 0 ? (
          <div className="flex flex-col gap-2">
            <h3 className="text-sm font-medium">{t('referrals.dashboard.topTitle')}</h3>
            <div className="-mx-5 overflow-x-auto sm:-mx-6">
              <table className="w-full min-w-[28rem] text-left text-sm">
                <thead className="border-b border-border text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('referrals.list.referrer')}</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">{t('referrals.dashboard.invited')}</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">{t('referrals.dashboard.came')}</th>
                    <th scope="col" className="px-5 py-2 text-right font-medium sm:px-6">{t('referrals.dashboard.returned')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {top.map((row) => (
                    <tr key={row.customerId}>
                      <td className="px-5 py-2 sm:px-6">
                        <Link to={`/customers/${row.customerId ?? ''}`} className="font-medium underline-offset-4 hover:underline">
                          {row.name || t('customers.noName')}
                        </Link>
                        <span className="block text-xs text-muted-foreground" dir="ltr">
                          {row.phone}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">{n(row.invited)}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{n(row.came)}</td>
                      <td className="px-5 py-2 text-right tabular-nums sm:px-6">{n(row.returned)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
