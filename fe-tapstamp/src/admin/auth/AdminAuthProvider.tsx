import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';

import { toSupportedLanguage } from '@/shared/i18n/languages';
import { changeLanguage, currentLanguage } from '@/shared/i18n/setup';

import { accountApi, authApi, contextApi, type ContextScope, type Me, type Realm, type SessionState } from '../api/adminApi';
import { setSetupCollapsed } from '../lib/setupCollapse';
import { sessionKeys } from './session';

interface AdminAuthValue {
  /** Domenə görə: şirkət domenində STAFF, platforma domenində PLATFORM. */
  realm: Realm;
  scope: ContextScope;
  session: SessionState | undefined;
  me: Me | undefined;
  /** Profil yüklənərkən xəta (şəbəkə və s.) — qorunan səhifələr təkrar cəhd təklif edir. */
  meError: boolean;
  retryMe: () => void;
  /** Giriş/MFA addımından sonra sessiya vəziyyətini yenidən oxuyur. */
  refresh: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthValue | null>(null);

export function useAdminAuth(): AdminAuthValue {
  const value = useContext(AdminAuthContext);
  if (!value) {
    throw new Error('useAdminAuth must be used inside AdminAuthProvider');
  }
  return value;
}

/** Daxil olmuş istifadəçi (yalnız qorunan səhifələrdə istifadə olunur — orada mütləq mövcuddur). */
export function useMe(): Me {
  const { me } = useAdminAuth();
  if (!me) {
    throw new Error('useMe called before the profile was loaded');
  }
  return me;
}

interface AdminAuthProviderProps {
  children: ReactNode;
  loading: ReactNode;
  renderError: (retry: () => void) => ReactNode;
}

export function AdminAuthProvider({ children, loading, renderError }: AdminAuthProviderProps) {
  const queryClient = useQueryClient();
  const context = useQuery({ queryKey: sessionKeys.context, queryFn: contextApi.context, staleTime: Infinity });
  const scope: ContextScope = context.data?.scope ?? 'TENANT';
  const realm: Realm = scope === 'PLATFORM' ? 'PLATFORM' : 'STAFF';

  const session = useQuery({
    queryKey: sessionKeys.session,
    queryFn: () => authApi.session(realm),
    enabled: context.isSuccess,
  });
  const me = useQuery({
    queryKey: sessionKeys.me,
    queryFn: () => accountApi.me(realm),
    enabled: session.data === 'ACTIVE',
  });

  // Sistemdən çıxanda və ya sessiya bitəndə başlanğıc siyahısının "Sonra davam et" seçimi silinir: yenidən girişdə tam açılır
  useEffect(() => {
    if (session.data !== undefined && session.data !== 'ACTIVE') {
      setSetupCollapsed(false);
    }
  }, [session.data]);

  // Profil dili (bütün cihazlarda eyni) interfeysə tətbiq olunur
  const profileLanguage = toSupportedLanguage(me.data?.preferredLanguage);
  useEffect(() => {
    if (profileLanguage && profileLanguage !== currentLanguage()) {
      void changeLanguage(profileLanguage);
    }
  }, [profileLanguage]);

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: sessionKeys.session });
    await queryClient.resetQueries({ queryKey: sessionKeys.me });
  }, [queryClient]);

  const value = useMemo<AdminAuthValue>(
    () => ({
      realm,
      scope,
      session: session.data,
      me: me.data,
      meError: me.isError,
      retryMe: () => void me.refetch(),
      refresh,
    }),
    [realm, scope, session.data, me, refresh],
  );

  if (context.isError || (session.isError && session.data === undefined)) {
    return renderError(() => {
      void context.refetch();
      void session.refetch();
    });
  }
  // Tam ekran yüklənmə yalnız ilk dəfə: sonrakı yeniləmələrdə açıq səhifə (məs. bərpa kodları) itməsin
  const initialLoad = context.isPending || session.data === undefined;
  return <AdminAuthContext.Provider value={value}>{initialLoad ? loading : children}</AdminAuthContext.Provider>;
}
