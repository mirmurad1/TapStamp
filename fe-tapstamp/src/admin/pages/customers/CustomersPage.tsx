import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { Download, Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { useFeature } from '@/shared/plan/plan';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Input, Select } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { contextApi, customersApi, type CustomerFilter, type CustomerSort } from '../../api/adminApi';
import { useMe } from '../../auth/AdminAuthProvider';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { hasAnyRole } from '../../layout/navigation';
import { useBranchNames } from '../../lib/branches';
import { downloadBlob } from '../../lib/download';
import { CustomerTable } from './CustomerTable';

const INACTIVE_OPTIONS = [14, 30, 60, 90] as const;

export function CustomersPage() {
  const { t } = useTranslation(['admin', 'common']);
  const me = useMe();
  // Hədiyyə üçün lazım olan möhür sayı (aktiv qayda) — kartdakı möhürlərin zolağı üçün
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company, staleTime: 5 * 60_000 });
  const required = company.data?.rewardRule?.stampsRequired ?? 0;
  const withStepUp = useStepUp();
  const { branches } = useBranchNames();
  const isOwner = hasAnyRole(me, ['COMPANY_OWNER']);
  const ownBranches = new Set((me.roles ?? []).map((grant) => grant.branchId).filter(Boolean));
  const branchOptions = isOwner ? branches : branches.filter((branch) => ownBranches.has(branch.id));

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<CustomerFilter>({});
  const [sort, setSort] = useState<CustomerSort>('REGISTERED_AT');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const paging = usePagination({ filter, sort, direction });

  const query = { ...filter, sort, direction, page: paging.page, size: paging.size };
  const customers = useQuery({ queryKey: ['customers', query], queryFn: () => customersApi.list(query), placeholderData: keepPreviousData });
  // CSV ixracı Standard və Premium paketlərdə
  const canExport = useFeature('CSV_EXPORT') === true;
  const exportCsv = useMutation({
    mutationFn: () => withStepUp(() => customersApi.exportCsv(filter)),
    onSuccess: (blob) => downloadBlob(blob, `customers-${new Date().toISOString().slice(0, 10)}.csv`),
  });

  const update = (patch: CustomerFilter) => {
    setFilter((current) => ({ ...current, ...patch }));
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
  };

  const data = customers.data;
  const total = data?.total ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('admin:customers.title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('admin:customers.subtitle')}</p>
        </div>
        {isOwner && canExport ? (
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
            <CustomerTable
              items={data.items ?? []}
              required={required}
              sort={sort}
              direction={direction}
              onSort={toggleSort}
              linkTo={(id) => `/customers/${id}`}
            />
          ) : null}

          <Pagination state={paging} total={total} />
        </CardContent>
      </Card>
    </div>
  );
}
