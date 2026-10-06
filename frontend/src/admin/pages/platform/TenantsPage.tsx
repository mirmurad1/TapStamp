import { useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';

import { platformApi } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { TENANTS_KEY } from './keys';
import { TenantStatusBadge } from './TenantStatusBadge';

/** Platforma panelinin ana səhifəsi: bütün şirkətlər. */
export function TenantsPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const tenants = useQuery({ queryKey: TENANTS_KEY, queryFn: platformApi.tenants });

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
          {tenants.data?.length === 0 ? <p className="text-sm text-muted-foreground">{t('platform.empty')}</p> : null}
          {tenants.data && tenants.data.length > 0 ? (
            <div className="-mx-5 overflow-x-auto sm:-mx-6">
              <table className="w-full min-w-[36rem] text-left text-sm">
                <thead className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.name')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.domain')}</th>
                    <th scope="col" className="px-3 py-2 font-medium">{t('platform.columns.language')}</th>
                    <th scope="col" className="px-5 py-2 font-medium sm:px-6">{t('platform.columns.status')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {tenants.data.map((tenant) => (
                    <tr key={tenant.id}>
                      <td className="px-5 py-3 sm:px-6">
                        <Link
                          to={`/tenants/${tenant.id}`}
                          className="font-medium underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {resolveText(tenant.displayName, lang)}
                        </Link>
                        <p className="font-mono text-xs text-muted-foreground">{tenant.slug}</p>
                      </td>
                      <td className="break-all px-3 py-3">{tenant.primaryDomain}</td>
                      <td className="px-3 py-3 uppercase">{tenant.defaultLanguage}</td>
                      <td className="px-5 py-3 sm:px-6">
                        <TenantStatusBadge status={tenant.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
