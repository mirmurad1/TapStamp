import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Input } from '@/shared/ui/input';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformApi, type CustomerSort } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { CustomerTable } from '../customers/CustomerTable';

/**
 * Platformada şirkətin müştəri siyahısı — şirkət panelindəki "Müştərilər" cədvəli ilə eyni. `onlyActive` — yalnız aktiv
 * (bloklanmamış) müştərilər. Telefonlar maskalanmış gəlir.
 */
export function TenantCustomersDialog({ tenantId, onlyActive, onClose }: { tenantId: string; onlyActive: boolean; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const [search, setSearch] = useState('');
  const [q, setQ] = useState<string>();
  const [sort, setSort] = useState<CustomerSort>('REGISTERED_AT');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const paging = usePagination({ q, sort, direction });

  const query = {
    q,
    status: onlyActive ? ('ACTIVE' as const) : undefined,
    sort,
    direction,
    page: paging.page,
    size: paging.size,
  };
  const list = useQuery({
    queryKey: ['platform-tenant-customers', tenantId, query],
    queryFn: () => platformApi.customers(tenantId, query),
    placeholderData: keepPreviousData,
  });
  const items = list.data?.customers?.items ?? [];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setQ(search.trim() || undefined);
  };
  const toggleSort = (field: CustomerSort) => {
    if (sort === field) {
      setDirection(direction === 'desc' ? 'asc' : 'desc');
    } else {
      setSort(field);
      setDirection(field === 'NAME' ? 'asc' : 'desc');
    }
  };

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-5xl">
        <DialogHeader>
          <DialogTitle>{t(onlyActive ? 'admin:platform.detail.customers' : 'admin:platform.detail.totalCustomers')}</DialogTitle>
          <DialogDescription>{t('admin:platform.detail.customersMasked')}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="flex gap-2" role="search">
          <Input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={t('admin:customers.searchPlaceholder')}
            aria-label={t('admin:customers.search')}
            maxLength={80}
          />
          <Button type="submit" variant="outline" aria-label={t('admin:customers.search')}>
            <Search className="size-4" aria-hidden="true" />
          </Button>
        </form>
        <ApiErrorAlert error={list.error} />
        {list.isPending ? <InlineLoading /> : null}
        {list.data && items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('admin:customers.empty')}</p>
        ) : null}
        {items.length > 0 ? (
          <div className="max-h-[60vh] overflow-y-auto px-5 sm:px-6">
            <CustomerTable
              items={items}
              required={list.data?.stampsRequired ?? 0}
              sort={sort}
              direction={direction}
              onSort={toggleSort}
            />
          </div>
        ) : null}
        <Pagination state={paging} total={list.data?.customers?.total ?? 0} />
      </DialogContent>
    </Dialog>
  );
}
