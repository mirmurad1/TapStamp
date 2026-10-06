import { Coffee } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router';

import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';

/** Müştəri tətbiqinin çərçivəsi: mobil üçün dar sütun, header-də həmişə görünən dil seçicisi. */
export function CustomerLayout() {
  const { t } = useTranslation('common');

  useEffect(() => {
    document.title = t('app.loyaltyCard');
  }, [t]);

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <header className="sticky top-0 z-10 -mx-4 flex items-center justify-between gap-3 bg-background/85 px-4 py-3 backdrop-blur">
        <Link
          to="/"
          className="flex min-h-11 items-center gap-2 rounded-full pr-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={t('app.loyaltyCard')}
        >
          <span className="grid size-10 place-items-center rounded-full bg-primary text-primary-foreground">
            <Coffee className="size-5" aria-hidden="true" />
          </span>
        </Link>
        <div className="flex items-center gap-1">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>
      <main className="flex flex-1 flex-col gap-4 pt-2">
        <Outlet />
      </main>
    </div>
  );
}
