import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation } from 'react-router';

import { FullPageError, FullPageLoading } from '../components/FullPageState';

import type { SessionState } from '../api/adminApi';
import { useAdminAuth } from './AdminAuthProvider';
import { pathForState } from './paths';

/** Yalnız tam daxil olmuş (MFA keçmiş) istifadəçi üçün səhifələr. */
export function RequireActive() {
  const { t } = useTranslation('errors');
  const { session, me, meError, retryMe } = useAdminAuth();
  const location = useLocation();
  if (session !== 'ACTIVE') {
    return <Navigate to={pathForState(session)} replace state={{ from: location.pathname }} />;
  }
  if (!me) {
    return meError ? <FullPageError message={t('UNKNOWN')} onRetry={retryMe} /> : <FullPageLoading />;
  }
  return <Outlet />;
}

/** Giriş addımının səhifəsi yalnız uyğun vəziyyətdə açılır; əks halda düzgün addıma yönləndirilir. */
export function RequireState({ allowed }: { allowed: readonly SessionState[] }) {
  const { session } = useAdminAuth();
  if (!session || !allowed.includes(session)) {
    return <Navigate to={pathForState(session)} replace />;
  }
  return <Outlet />;
}
