import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router';

import { Button } from '@/shared/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import type { ContextScope } from '../api/adminApi';
import { useAdminAuth, useMe } from '../auth/AdminAuthProvider';
import { hasAnyRole } from '../layout/navigation';
import { DashboardPage } from '../pages/DashboardPage';
import { TenantsPage } from '../pages/platform/TenantsPage';

export function NotFound() {
  const { t } = useTranslation('common');
  return (
    <Card className="max-w-lg">
      <CardHeader>
        <CardTitle>{t('notFound.title')}</CardTitle>
        <CardDescription>{t('notFound.description')}</CardDescription>
      </CardHeader>
      <div className="px-5 pb-5 sm:px-6 sm:pb-6">
        <Button asChild variant="outline">
          <Link to="/">{t('notFound.home')}</Link>
        </Button>
      </div>
    </Card>
  );
}

export function RouteError() {
  const { t } = useTranslation(['errors', 'common']);
  return (
    <div className="grid min-h-dvh place-items-center px-4">
      <div className="flex max-w-sm flex-col items-center gap-4 text-center">
        <p>{t('errors:UNKNOWN')}</p>
        <Button onClick={() => window.location.reload()}>{t('common:actions.retry')}</Button>
      </div>
    </div>
  );
}

/** Bölmə yalnız müəyyən domendə (şirkət və ya platforma) mövcuddur. */
export function RequireScope({ scope }: { scope: ContextScope }) {
  const { scope: current } = useAdminAuth();
  return current === scope ? <Outlet /> : <NotFound />;
}

/** Menyuda görünməyən bölməyə birbaşa keçid: rol uyğun deyilsə, "tapılmadı" (backend də 403 qaytarır). */
export function RequireRole({ roles }: { roles: readonly string[] }) {
  const me = useMe();
  return hasAnyRole(me, roles) ? <Outlet /> : <NotFound />;
}

export function HomePage() {
  const { scope } = useAdminAuth();
  return scope === 'PLATFORM' ? <TenantsPage /> : <DashboardPage />;
}
