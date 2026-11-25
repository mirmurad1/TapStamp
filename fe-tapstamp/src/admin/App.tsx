import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { RouterProvider } from 'react-router';

import { PlanGate } from '@/shared/plan/PlanGate';
import { ConfirmProvider } from '@/shared/ui/ConfirmProvider';

import { contextApi } from './api/adminApi';
import { AdminAuthProvider } from './auth/AdminAuthProvider';
import { sessionKeys } from './auth/session';
import { FullPageError, FullPageLoading } from './components/FullPageState';
import { adminRouter } from './router';

/**
 * Admin tətbiqi: əvvəlcə şirkətin paketi (sınaq bitibsə — dayandırılma ekranı), sonra domenin konteksti və sessiyanın
 * vəziyyəti, sonra marşrutlar.
 */
export function App() {
  const { t } = useTranslation('errors');
  // Platforma domenində şirkət paketi yoxdur — paket yoxlaması (və /public/plan sorğusu) yalnız şirkət domenində
  const context = useQuery({ queryKey: sessionKeys.context, queryFn: contextApi.context, staleTime: Infinity });
  if (context.isPending) {
    return <FullPageLoading />;
  }
  return (
    <PlanGate audience="staff" loading={<FullPageLoading />} enabled={context.data?.scope !== 'PLATFORM'}>
      <AdminAuthProvider
        loading={<FullPageLoading />}
        renderError={(retry) => <FullPageError message={t('NETWORK_ERROR')} onRetry={retry} />}
      >
        <ConfirmProvider>
          <RouterProvider router={adminRouter} />
        </ConfirmProvider>
      </AdminAuthProvider>
    </PlanGate>
  );
}
