import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';

import { toSupportedLanguage } from '@/shared/i18n/languages';
import { changeLanguage, currentLanguage } from '@/shared/i18n/setup';

import { customerAuthApi, meApi, type Me } from '../api/customerApi';
import { clearCustomerData, customerKeys } from './queryClient';
import { refreshSession } from './refresh';

interface CustomerSession {
  /** loading — ilk yoxlama; guest — daxil olmayıb; signedIn — profil yüklənib. */
  status: 'loading' | 'guest' | 'signedIn';
  me: Me | undefined;
  /** Giriş və ya qeydiyyatdan sonra sessiyanı yenidən oxuyur. */
  refresh: () => Promise<void>;
  /** Çıxış və ya hesabın silinməsindən sonra: bütün şəxsi məlumat keşdən silinir. */
  clear: () => void;
}

const SessionContext = createContext<CustomerSession | null>(null);

export function useCustomerSession(): CustomerSession {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useCustomerSession must be used inside CustomerSessionProvider');
  }
  return value;
}

/** Müştərinin sessiyası: access token-in vaxtı keçibsə, səhifə açılanda bir dəfə yenilənir. */
export function CustomerSessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useQuery({
    queryKey: customerKeys.session,
    queryFn: async () => {
      const state = await customerAuthApi.session();
      if (state === 'REFRESH_REQUIRED') {
        return (await refreshSession()) ? customerAuthApi.session() : 'NONE';
      }
      return state;
    },
    staleTime: 60_000,
  });
  const me = useQuery({ queryKey: customerKeys.me, queryFn: meApi.me, enabled: session.data === 'ACTIVE' });

  // Profil dili (bütün cihazlarda eyni) interfeysə tətbiq olunur
  const profileLanguage = toSupportedLanguage(me.data?.preferredLanguage);
  useEffect(() => {
    if (profileLanguage && profileLanguage !== currentLanguage()) {
      void changeLanguage(profileLanguage);
    }
  }, [profileLanguage]);

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: customerKeys.session });
    await queryClient.resetQueries({ queryKey: customerKeys.me });
  }, [queryClient]);

  const clear = useCallback(() => clearCustomerData(queryClient), [queryClient]);

  let status: CustomerSession['status'];
  if (session.data === 'ACTIVE' && me.data) {
    status = 'signedIn';
  } else if (session.isPending || (session.data === 'ACTIVE' && me.isPending)) {
    status = 'loading';
  } else {
    status = 'guest';
  }

  const value = useMemo(() => ({ status, me: me.data, refresh, clear }), [status, me.data, refresh, clear]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
