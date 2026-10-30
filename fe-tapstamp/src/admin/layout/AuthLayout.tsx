import { useQuery } from '@tanstack/react-query';
import { LayoutGrid, ShieldCheck } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { applyBrandColor } from '@/shared/lib/brand';
import { Footer } from '@/shared/ui/Footer';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';

import { contextApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthProvider';

/** Giriş, MFA, şifrə bərpası və dəvət səhifələrinin çərçivəsi: mərkəzdə kart, yuxarıda şirkətin loqosu və adı. */
export function AuthLayout() {
  const { t, i18n } = useTranslation('admin');
  const { scope } = useAdminAuth();
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company, enabled: scope === 'TENANT' });
  const title = scope === 'PLATFORM' ? t('shell.platformTitle') : resolveText(company.data?.name, currentLanguage());

  // Şirkətin brend rəngi giriş səhifəsində də tətbiq olunur — yalnız paneldən qayıdanda deyil, refresh-dən sonra da
  const brandColor = company.data?.brandColor;
  useEffect(() => applyBrandColor(brandColor), [brandColor]);
  const logoUrl = scope === 'TENANT' ? company.data?.logoUrl : undefined;

  useEffect(() => {
    document.title = title ? `${title} · ${t('shell.title')}` : t('shell.title');
  }, [title, t, i18n.language]);

  const Icon = scope === 'PLATFORM' ? ShieldCheck : LayoutGrid;
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between gap-2 px-4 py-3 sm:px-6">
        <div className="flex min-w-0 items-center gap-2">
          {logoUrl ? (
            <img src={logoUrl} alt="" className="size-9 shrink-0 rounded-full object-cover ring-1 ring-border" />
          ) : (
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
              <Icon className="size-5" aria-hidden="true" />
            </span>
          )}
          <span className="truncate font-display text-lg font-semibold">{title}</span>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <LanguageSwitcher />
        </div>
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pb-10 pt-6 sm:items-center sm:pt-0">
        <div className="w-full max-w-md">
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  );
}
