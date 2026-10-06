import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Coffee, ShieldCheck } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet } from 'react-router';

import type { Language } from '@/shared/i18n/languages';
import { cn } from '@/shared/lib/cn';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';

import { accountApi } from '../api/adminApi';
import { useAdminAuth, useMe } from '../auth/AdminAuthProvider';
import { sessionKeys } from '../auth/session';
import { StepUpProvider } from '../auth/StepUpProvider';
import { navigationFor } from './navigation';
import { UserMenu } from './UserMenu';

/** Admin panelinin çərçivəsi: sol menyu (rollara görə), yuxarı panel (dil, mövzu, istifadəçi menyusu). */
export function AdminLayout() {
  const { t } = useTranslation('admin');
  const { scope, realm } = useAdminAuth();
  const me = useMe();
  const queryClient = useQueryClient();
  const items = navigationFor(scope, me);
  const title = scope === 'PLATFORM' ? t('shell.platformTitle') : t('shell.title');

  // Dil seçimi profilə yazılır: növbəti girişdə və digər cihazlarda da eyni dil açılır
  const saveLanguage = useMutation({
    mutationFn: (lang: Language) => accountApi.updateMe(realm, { preferredLanguage: lang }),
    onSuccess: (updated) => queryClient.setQueryData(sessionKeys.me, updated),
  });

  useEffect(() => {
    document.title = title;
  }, [title]);

  const Icon = scope === 'PLATFORM' ? ShieldCheck : Coffee;
  return (
    <StepUpProvider>
      <div className="flex min-h-dvh flex-col md:flex-row">
        <aside className="border-b border-border bg-card md:sticky md:top-0 md:h-dvh md:w-64 md:shrink-0 md:border-b-0 md:border-r">
          <div className="flex items-center gap-2 px-5 py-4">
            <span className="grid size-9 place-items-center rounded-full bg-primary text-primary-foreground">
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <span className="font-display text-lg font-semibold">{title}</span>
          </div>
          <nav aria-label={t('shell.mainNav')} className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible">
            {items.map(({ section, to, icon: ItemIcon }) => (
              <NavLink
                key={section}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-10 shrink-0 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <ItemIcon className="size-4 shrink-0" aria-hidden="true" />
                <span>{t(`nav.${section}`)}</span>
              </NavLink>
            ))}
          </nav>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex flex-wrap items-center justify-end gap-2 border-b border-border px-4 py-3 md:px-8">
            <LanguageSwitcher onSelect={(lang) => saveLanguage.mutate(lang)} />
            <ThemeToggle />
            <UserMenu />
          </header>
          <main className="flex-1 px-4 py-6 md:px-8">
            <Outlet />
          </main>
        </div>
      </div>
    </StepUpProvider>
  );
}
