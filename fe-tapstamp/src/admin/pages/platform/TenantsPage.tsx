import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { platformApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { TENANTS_KEY } from './keys';
import { TenantLogo } from './TenantLogo';
import { TenantPlanSummary } from './TenantPlanCard';
import { TenantStatusBadge } from './TenantStatusBadge';

/** Platforma panelinin ana səhifəsi: bütün şirkətlər. */
export function TenantsPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const navigate = useNavigate();
  const paging = usePagination();
  const tenants = useQuery({
    queryKey: [...TENANTS_KEY, paging.page, paging.size],
    queryFn: () => platformApi.tenants({ page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = tenants.data?.items ?? [];

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
      <Card>
        <CardContent className="pt-5 sm:pt-6">
          <ApiErrorAlert error={tenants.error} />
          {tenants.isPending ? <InlineLoading /> : null}
          {tenants.data && items.length === 0 ? <p className="text-sm text-muted-foreground">{t('platform.empty')}</p> : null}
          {items.length > 0 ? (
            <div className="-mx-5 overflow-x-auto sm:-mx-6" tabIndex={0}>
              <table className="w-full min-w-[44rem] text-left text-sm">
                <thead className="border-b border-border text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.name')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.domain')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.language')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.plan')}</th>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.status')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {items.map((tenant) => {
                    const name = resolveText(tenant.displayName, lang);
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
                      <td className="px-3 py-3 uppercase text-muted-foreground">{tenant.defaultLanguage}</td>
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
