import { useTranslation } from 'react-i18next';
import { RouterProvider } from 'react-router';

import { ConfirmProvider } from '@/shared/ui/ConfirmProvider';

import { AdminAuthProvider } from './auth/AdminAuthProvider';
import { FullPageError, FullPageLoading } from './components/FullPageState';
import { adminRouter } from './router';

/** Admin tətbiqi: əvvəlcə domenin konteksti və sessiyanın vəziyyəti, sonra marşrutlar. */
export function App() {
  const { t } = useTranslation('errors');
  return (
    <AdminAuthProvider
      loading={<FullPageLoading />}
      renderError={(retry) => <FullPageError message={t('NETWORK_ERROR')} onRetry={retry} />}
    >
      <ConfirmProvider>
        <RouterProvider router={adminRouter} />
      </ConfirmProvider>
    </AdminAuthProvider>
  );
}
