import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Building2, CalendarClock, CalendarX2, Plus, Search, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { EmptyState } from '../../components/EmptyState';
import { InlineLoading } from '../../components/FullPageState';
import { Kpi } from '../../components/Kpi';
import { TENANTS_KEY } from './keys';
import { TenantLogo } from './TenantLogo';
import { TenantPlanSummary } from './TenantPlanCard';
import { TenantStatusBadge } from './TenantStatusBadge';

const STATUSES = ['ACTIVE', 'SUSPENDED'] as const;

/** Platforma panelinin ana səhifəsi: ümumi göstəricilər və şirkətlərin siyahısı (axtarış, status süzgəci). */
export function TenantsPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  // Yazmağı bitirəndə axtar: hər hərfdə sorğu göndərilmir
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const filter = { q: q || undefined, status: status || undefined };
  const paging = usePagination(filter);
  const tenants = useQuery({
    queryKey: [...TENANTS_KEY, filter, paging.page, paging.size],
    queryFn: () => platformApi.tenants({ ...filter, page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const overview = useQuery({ queryKey: [...TENANTS_KEY, 'overview'], queryFn: platformApi.overview });
  const items = tenants.data?.items ?? [];
  const counts = overview.data;
  const filtered = Boolean(q || status);
  const clearFilters = () => {
    setSearch('');
    setQ('');
    setStatus('');
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('platform.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('platform.subtitle')}</p>
        </div>
        <Button asChild>
          <Link to="/tenants/new">
            <Plus aria-hidden="true" />
            {t('platform.newTenant')}
          </Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi
          icon={<Building2 />}
          label={t('platform.overview.tenants')}
          value={formatNumber(counts?.tenants ?? 0, lang)}
          loading={overview.isPending}
        >
          {t('platform.overview.tenantsHint', { active: counts?.active ?? 0, suspended: counts?.suspended ?? 0 })}
        </Kpi>
        <Kpi
          icon={<CalendarClock />}
          label={t('platform.overview.trialEnding')}
          value={formatNumber(counts?.trialEndingSoon ?? 0, lang)}
          loading={overview.isPending}
        >
          {t('platform.overview.trialEndingHint')}
        </Kpi>
        <Kpi
          icon={<CalendarX2 />}
          label={t('platform.overview.expired')}
          value={formatNumber(counts?.expired ?? 0, lang)}
          loading={overview.isPending}
        >
          {t('platform.overview.expiredHint')}
        </Kpi>
        <Kpi
          icon={<Users />}
          label={t('platform.overview.customers')}
          value={formatNumber(counts?.totalCustomers ?? 0, lang)}
          loading={overview.isPending}
        >
          {t('platform.overview.customersHint')}
        </Kpi>
      </div>

      <Card>
        <CardContent className="flex flex-col gap-4 pt-5 sm:pt-6">
          <div className="flex flex-wrap items-end gap-3" role="search">
            <label className="flex min-w-60 flex-1 flex-col gap-1 text-sm font-medium">
              {t('platform.search')}
              <span className="relative">
                <Search
                  className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
                  aria-hidden="true"
                />
                <Input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={t('platform.searchHint')}
                  className="pl-9"
                />
              </span>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('platform.columns.status')}
              <Select value={status} onChange={(event) => setStatus(event.target.value)}>
                <option value="">{t('customers.filters.any')}</option>
                {STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {t(`platform.status.${value}`)}
                  </option>
                ))}
              </Select>
            </label>
          </div>
          <ApiErrorAlert error={tenants.error ?? overview.error} />
          {tenants.isPending ? <InlineLoading /> : null}
          {tenants.data && items.length === 0 ? (
            <EmptyState
              icon={<Building2 />}
              message={filtered ? t('platform.noMatches') : t('platform.empty')}
              action={filtered ? { label: t('platform.clearFilters'), onClick: clearFilters } : undefined}
            />
          ) : null}
          {items.length > 0 ? (
            <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
              <table className="w-full min-w-[44rem] text-left text-sm">
                <thead className="border-b border-border text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.name')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.domain')}</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">{t('platform.columns.customers')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.plan')}</th>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.status')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((tenant) => {
                    const name = resolveText(tenant.displayName, lang);
                    const customers = tenant.id ? counts?.customersByTenant?.[tenant.id] : undefined;
                    return (
                      // Bütün sətir kliklənir; ad keçidi klaviatura və ekran oxuyucusu üçün qalır
                      <tr
                        key={tenant.id}
                        className="cursor-pointer transition-colors hover:bg-muted/50"
                        onClick={() => void navigate(`/tenants/${tenant.id}`)}
                      >
                        <td className="px-5 py-3 sm:px-6">
                          <div className="flex items-center gap-3">
                            <TenantLogo tenantId={tenant.id ?? ''} name={name} lang={lang} className="size-9 rounded-xl text-sm" />
                            <div className="min-w-0">
                              <Link
                                to={`/tenants/${tenant.id}`}
                                onClick={(event) => event.stopPropagation()}
                                className="font-semibold underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                              >
                                {name}
                              </Link>
                              <p className="font-mono text-xs text-muted-foreground">{tenant.slug}</p>
                            </div>
                          </div>
                        </td>
                        <td className="break-all px-3 py-3 text-muted-foreground">{tenant.primaryDomain}</td>
                        <td className="px-3 py-3 text-right tabular-nums">
                          {customers === undefined ? <span className="text-muted-foreground">—</span> : formatNumber(customers, lang)}
                        </td>
                        <td className="px-3 py-3">
                          <TenantPlanSummary tenant={tenant} />
                        </td>
                        <td className="px-5 py-3 sm:px-6">
                          <TenantStatusBadge status={tenant.status} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : null}
          <Pagination state={paging} total={tenants.data?.total ?? 0} />
        </CardContent>
      </Card>
    </div>
  );
}
