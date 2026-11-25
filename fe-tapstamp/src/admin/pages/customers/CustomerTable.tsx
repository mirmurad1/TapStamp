import { ArrowDown, ArrowUp, ShieldAlert } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate, formatNumber } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/badge';

import type { CustomerSort, customersApi } from '../../api/adminApi';

/** Cədvəlin sütunları; field — sıralana bilən sahə. */
const HEADERS: readonly {
  field?: CustomerSort;
  label:
    | 'admin:customers.columns.customer'
    | 'admin:customers.columns.language'
    | 'admin:customers.columns.stamps'
    | 'admin:customers.columns.rewards'
    | 'admin:customers.columns.lastVisit'
    | 'admin:customers.columns.registered';
  className: string;
}[] = [
  { field: 'NAME', label: 'admin:customers.columns.customer', className: 'px-5 py-2 sm:px-6' },
  { label: 'admin:customers.columns.language', className: 'px-3 py-2' },
  { field: 'TOTAL_STAMPS', label: 'admin:customers.columns.stamps', className: 'px-3 py-2' },
  { label: 'admin:customers.columns.rewards', className: 'px-3 py-2' },
  { field: 'LAST_VISIT_AT', label: 'admin:customers.columns.lastVisit', className: 'px-3 py-2' },
  { field: 'REGISTERED_AT', label: 'admin:customers.columns.registered', className: 'px-5 py-2 sm:px-6' },
];

type CustomerRow = NonNullable<Awaited<ReturnType<typeof customersApi.list>>['items']>[number];

/**
 * Müştərilər cədvəli — şirkət panelindəki "Müştərilər" ekranında və platformada şirkətin səhifəsində eyni görünüş.
 * `linkTo` verilməsə, ad keçid deyil (platformada müştərinin detal səhifəsi yoxdur).
 */
export function CustomerTable({
  items,
  required,
  sort,
  direction,
  onSort,
  linkTo,
}: {
  items: CustomerRow[];
  required: number;
  sort: CustomerSort;
  direction: 'asc' | 'desc';
  onSort: (field: CustomerSort) => void;
  linkTo?: (customerId: string) => string;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();

  const sortHeader = (field: CustomerSort, label: string, className: string) => (
    <th key={field} scope="col" className={className} aria-sort={sort === field ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="inline-flex items-center gap-1 font-medium hover:text-foreground" onClick={() => onSort(field)}>
        {label}
        {sort === field ? (
          direction === 'asc' ? <ArrowUp className="size-3" aria-hidden="true" /> : <ArrowDown className="size-3" aria-hidden="true" />
        ) : null}
      </button>
    </th>
  );

  return (
    <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
      <table className="w-full min-w-[52rem] text-left text-sm">
        <thead className="border-b border-border text-xs text-muted-foreground">
          <tr>
            {HEADERS.map((header) =>
              header.field ? (
                sortHeader(header.field, t(header.label), header.className)
              ) : (
                <th key={header.label} scope="col" className={cn(header.className, 'font-medium')}>
                  {t(header.label)}
                </th>
              ),
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {items.map((customer) => {
            const name = customer.name || t('admin:customers.noName');
            return (
              <tr key={customer.id} className="hover:bg-muted/50">
                <td className="px-5 py-3 sm:px-6">
                  {linkTo ? (
                    <Link to={linkTo(customer.id ?? '')} className="font-semibold text-foreground underline-offset-4 hover:underline">
                      {name}
                    </Link>
                  ) : (
                    <span className="font-semibold text-foreground">{name}</span>
                  )}
                  <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
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
                  {/* Kartdakı möhürlər zolaqla (hədiyyəyə qədər), altında ümumi say */}
                  <span className="flex items-center gap-2">
                    <span className="font-medium">
                      {customer.currentStamps ?? 0}/{required || '–'}
                    </span>
                    {required ? (
                      <span className="h-1.5 w-16 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                        <span
                          className="block h-full rounded-full bg-primary"
                          style={{ width: `${Math.min(100, ((customer.currentStamps ?? 0) / required) * 100)}%` }}
                        />
                      </span>
                    ) : null}
                  </span>
                  <span className="block text-xs text-muted-foreground">
                    {t('admin:customers.stampsTotal', { total: formatNumber(customer.totalStamps ?? 0, lang) })}
                  </span>
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
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
