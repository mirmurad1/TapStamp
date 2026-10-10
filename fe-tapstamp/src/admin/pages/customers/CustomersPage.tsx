import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Download, Search, ShieldAlert } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate, formatNumber } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Input, Select } from '@/shared/ui/input';

import { customersApi, type CustomerFilter, type CustomerSort } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { hasAnyRole } from '../../layout/navigation';
import { useBranchNames } from '../../lib/branches';
import { downloadBlob } from '../../lib/download';

const PAGE_SIZE = 25;
/** Cədvəlin sütunları; field — sıralana bilən sahə. */
const HEADERS: readonly { field?: CustomerSort; label: 'admin:customers.columns.customer' | 'admin:customers.columns.language' | 'admin:customers.columns.stamps' | 'admin:customers.columns.rewards' | 'admin:customers.columns.lastVisit' | 'admin:customers.columns.registered'; className: string }[] = [
  { field: 'NAME', label: 'admin:customers.columns.customer', className: 'px-5 py-2 sm:px-6' },
  { label: 'admin:customers.columns.language', className: 'px-3 py-2' },
  { field: 'TOTAL_STAMPS', label: 'admin:customers.columns.stamps', className: 'px-3 py-2' },
  { label: 'admin:customers.columns.rewards', className: 'px-3 py-2' },
  { field: 'LAST_VISIT_AT', label: 'admin:customers.columns.lastVisit', className: 'px-3 py-2' },
  { field: 'REGISTERED_AT', label: 'admin:customers.columns.registered', className: 'px-5 py-2 sm:px-6' },
];
const INACTIVE_OPTIONS = [14, 30, 60, 90] as const;

export function CustomersPage() {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  const lang = currentLanguage();
  const withStepUp = useStepUp();
  const { branches } = useBranchNames();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const ownBranches = new Set((me.roles ?? []).map((grant) => grant.branchId).filter(Boolean));
  const branchOptions = isOwner ? branches : branches.filter((branch) => ownBranches.has(branch.id));

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<CustomerFilter>({});
  const [sort, setSort] = useState<CustomerSort>('REGISTERED_AT');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [page, setPage] = useState(0);

  const query = { ...filter, sort, direction, page, size: PAGE_SIZE };
  const customers = useQuery({ queryKey: ['customers', query], queryFn: () => customersApi.list(query), placeholderData: (prev) => prev });
  const exportCsv = useMutation({
    mutationFn: () => withStepUp(() => customersApi.exportCsv(filter)),
    onSuccess: (blob) => downloadBlob(blob, `customers-${new Date().toISOString().slice(0, 10)}.csv`),
  });

  const update = (patch: CustomerFilter) => {
    setFilter((current) => ({ ...current, ...patch }));
    setPage(0);
  };
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    update({ q: search.trim() || undefined });
  };
  const toggleSort = (field: CustomerSort) => {
    if (sort === field) {
      setDirection(direction === 'desc' ? 'asc' : 'desc');
    } else {
      setSort(field);
      setDirection(field === 'NAME' ? 'asc' : 'desc');
    }
    setPage(0);
  };

  const data = customers.data;
  const total = data?.total ?? 0;
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const to = Math.min((page + 1) * PAGE_SIZE, total);

  const sortHeader = (field: CustomerSort, label: string, className: string) => (
    <th key={field} scope="col" className={className} aria-sort={sort === field ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="inline-flex items-center gap-1 font-medium uppercase hover:text-foreground" onClick={() => toggleSort(field)}>
        {label}
        {sort === field ? (
          direction === 'asc' ? <ArrowUp className="size-3" aria-hidden="true" /> : <ArrowDown className="size-3" aria-hidden="true" />
        ) : null}
      </button>
    </th>
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('admin:customers.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('admin:customers.subtitle')}</p>
        </div>
        {isOwner ? (
          <Button variant="outline" onClick={() => exportCsv.mutate()} disabled={exportCsv.isPending}>
            <Download className="size-4" aria-hidden="true" />
            {t('admin:customers.export')}
          </Button>
        ) : null}
      </div>

      <Card>
        <CardContent className="flex flex-col gap-4 pt-5 sm:pt-6">
          <form onSubmit={submitSearch} className="flex flex-wrap items-end gap-3" role="search">
            <label className="flex min-w-60 flex-1 flex-col gap-1 text-sm font-medium">
              {t('admin:customers.search')}
              <div className="flex gap-2">
                <Input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={t('admin:customers.searchPlaceholder')}
                  maxLength={80}
                />
                <Button type="submit" variant="outline" aria-label={t('admin:customers.search')}>
                  <Search className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:customers.filters.language')}
              <Select value={filter.language ?? ''} onChange={(event) => update({ language: event.target.value || undefined })}>
                <option value="">{t('admin:customers.filters.any')}</option>
                {SUPPORTED_LANGUAGES.map((code) => (
                  <option key={code} value={code}>
                    {t(`common:language.${code}`)}
                  </option>
                ))}
              </Select>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:customers.filters.status')}
              <Select value={filter.status ?? ''} onChange={(event) => update({ status: event.target.value || undefined })}>
                <option value="">{t('admin:customers.filters.any')}</option>
                <option value="ACTIVE">{t('admin:customers.status.ACTIVE')}</option>
                <option value="BLOCKED">{t('admin:customers.status.BLOCKED')}</option>
              </Select>
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              {t('admin:customers.filters.inactive')}
              <Select
                value={filter.inactiveDays ?? ''}
                onChange={(event) => update({ inactiveDays: event.target.value ? Number(event.target.value) : undefined })}
              >
                <option value="">{t('admin:customers.filters.any')}</option>
                {INACTIVE_OPTIONS.map((days) => (
                  <option key={days} value={days}>
                    {t('admin:customers.filters.inactiveDays', { count: days })}
                  </option>
                ))}
              </Select>
            </label>
            {branchOptions.length > 1 ? (
              <label className="flex flex-col gap-1 text-sm font-medium">
                {t('admin:dashboard.filters.branch')}
                <Select value={filter.branchId ?? ''} onChange={(event) => update({ branchId: event.target.value || undefined })}>
                  <option value="">{t('admin:dashboard.filters.allBranches')}</option>
                  {branchOptions.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </Select>
              </label>
            ) : null}
          </form>

          <ApiErrorAlert error={customers.error ?? exportCsv.error} />
          {customers.isPending ? <InlineLoading /> : null}

          {data && data.items?.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{t('admin:customers.empty')}</p>
          ) : null}

          {data && (data.items?.length ?? 0) > 0 ? (
            <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
              <table className="w-full min-w-[52rem] text-left text-sm">
                <thead className="border-b border-border text-xs tracking-wide text-muted-foreground">
                  <tr>
                    {HEADERS.map((header) =>
                      header.field ? (
                        sortHeader(header.field, t(header.label), header.className)
                      ) : (
                        <th key={header.label} scope="col" className={cn(header.className, 'font-medium uppercase')}>
                          {t(header.label)}
                        </th>
                      ),
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {data.items?.map((customer) => (
                    <tr key={customer.id} className="hover:bg-muted/50">
                      <td className="px-5 py-3 sm:px-6">
                        <Link to={`/customers/${customer.id}`} className="font-medium text-primary hover:underline">
                          {customer.name || t('admin:customers.noName')}
                        </Link>
                        <p className="flex flex-wrap items-center gap-2 text-muted-foreground">
                          <span className="tabular-nums">{customer.phone}</span>
                          {customer.status === 'BLOCKED' ? <Badge tone="danger">{t('admin:customers.status.BLOCKED')}</Badge> : null}
                          {(customer.openFraudFlags ?? 0) > 0 ? (
                            <Badge tone="warning">
                              <ShieldAlert className="mr-1 size-3" aria-hidden="true" />
                              {t('admin:customers.flags', { count: customer.openFraudFlags })}
                            </Badge>
                          ) : null}
                        </p>
                      </td>
                      <td className="px-3 py-3 uppercase text-muted-foreground">{customer.language}</td>
                      <td className="px-3 py-3 tabular-nums">
                        {t('admin:customers.stampsValue', { current: customer.currentStamps ?? 0, total: customer.totalStamps ?? 0 })}
                      </td>
                      <td className="px-3 py-3 tabular-nums">
                        {formatNumber(customer.rewardsRedeemed ?? 0, lang)} / {formatNumber(customer.rewardsEarned ?? 0, lang)}
                      </td>
                      <td className="px-3 py-3 text-muted-foreground">
                        {customer.lastVisitAt ? formatDate(customer.lastVisitAt, lang) : t('admin:team.never')}
                      </td>
                      <td className="px-5 py-3 text-muted-foreground sm:px-6">
                        {customer.registeredAt ? formatDate(customer.registeredAt, lang) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          {total > 0 ? (
            <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
              <span>{t('admin:customers.range', { from, to, total: formatNumber(total, lang) })}</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  <ChevronLeft className="size-4" aria-hidden="true" />
                  {t('admin:customers.previous')}
                </Button>
                <Button variant="outline" size="sm" disabled={to >= total} onClick={() => setPage(page + 1)}>
                  {t('admin:customers.next')}
                  <ChevronRight className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
