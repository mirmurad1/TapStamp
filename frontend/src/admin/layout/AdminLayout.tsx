import { Coffee } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet } from 'react-router';

import { cn } from '@/shared/lib/cn';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';

import { NAV_ITEMS } from './navigation';

/** Admin panelinin çərçivəsi: sol menyu (desktop-first) və yuxarı panel (dil seçicisi, mövzu). */
export function AdminLayout() {
  const { t } = useTranslation('admin');

  useEffect(() => {
    document.title = t('shell.title');
  }, [t]);

  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="border-b border-border bg-card md:sticky md:top-0 md:h-dvh md:w-64 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center gap-2 px-5 py-4">
          <span className="grid size-9 place-items-center rounded-full bg-primary text-primary-foreground">
            <Coffee className="size-5" aria-hidden="true" />
          </span>
          <span className="font-display text-lg font-semibold">{t('shell.title')}</span>
        </div>
        <nav aria-label={t('shell.mainNav')} className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible">
          {NAV_ITEMS.map(({ section, to, icon: Icon }) => (
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
              <Icon className="size-4 shrink-0" aria-hidden="true" />
              <span>{t(`nav.${section}`)}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-end gap-2 border-b border-border px-4 py-3 md:px-8">
          <LanguageSwitcher />
          <ThemeToggle />
        </header>
        <main className="flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
