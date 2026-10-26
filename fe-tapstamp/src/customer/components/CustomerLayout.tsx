import { useQuery } from '@tanstack/react-query';
import { Gift, Settings, Stamp } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, ScrollRestoration, useLocation } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { applyBrandColor, applyIconVersion } from '@/shared/lib/brand';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Footer } from '@/shared/ui/Footer';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';

import { publicApi } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { ConsentPage } from '../pages/ConsentPage';

/** Müştəri tətbiqinin çərçivəsi: mobil üçün dar sütun; header-də şirkət, hədiyyə kartları və parametrlər. */
export function CustomerLayout() {
  const { t, i18n } = useTranslation(['common', 'profile']);
  const { status, me } = useCustomerSession();
  const location = useLocation();
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const companyName = resolveText(company.data?.name, currentLanguage());
  const readyGifts = me?.availableRewards?.length ?? 0;
  const accountDeleted = Boolean((location.state as { accountDeleted?: boolean } | null)?.accountDeleted);


  // Şirkətin brend rəngi tətbiqin əsas rəngi olur
  const brandColor = company.data?.brandColor;
  useEffect(() => applyBrandColor(brandColor), [brandColor]);
  // Ana ekran ikonu (iPhone) rəng və ya loqo dəyişəndə yenisi ilə əvəz olunsun
  const logoUrl = company.data?.logoUrl;
  useEffect(() => applyIconVersion(brandColor, logoUrl), [brandColor, logoUrl]);

  useEffect(() => {
    document.title = companyName ? `${companyName} · ${t('common:app.loyaltyCard')}` : t('common:app.loyaltyCard');
  }, [companyName, t, i18n.language]);

  // Yeni versiyalı sənədlərə razılıq verilməyibsə — əvvəlcə razılıq (hüquqi sənədlər açıq qalır)
  const pendingConsents = me?.pendingConsents ?? [];
  const consentGate = status === 'signedIn' && pendingConsents.length > 0 && !location.pathname.startsWith('/legal/');

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      {/* Səhifələr arasında keçəndə scroll yeni səhifədə yuxarıdan başlayır, "geri"də əvvəlki yerinə qayıdır */}
      <ScrollRestoration />
      {/* Başlıq tam rəngli və öz təbəqəsindədir: iOS Safari-də sticky + backdrop-blur geri qayıdanda başlığı
          scroll edilənə qədər çəkmir */}
      <header className="sticky top-0 z-10 -mx-4 flex items-center justify-between gap-3 bg-background px-4 py-3 [transform:translateZ(0)]">
        <Link
          to="/"
          className="flex min-h-11 min-w-0 items-center gap-2 rounded-full pr-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={t('profile:nav.home')}
        >
          {/* Şirkət loqosu yükləyibsə onun şəkli, əks halda standart möhür ikonu */}
          <span className="grid size-10 shrink-0 place-items-center overflow-hidden rounded-full bg-primary text-primary-foreground">
            {company.data?.logoUrl ? (
              <img src={company.data.logoUrl} alt="" className="size-full object-cover" />
            ) : (
              <Stamp className="size-5" aria-hidden="true" />
            )}
          </span>
          {companyName ? <span className="line-clamp-2 break-words font-display leading-tight font-bold">{companyName}</span> : null}
        </Link>
        <div className="flex shrink-0 items-center gap-1">
          {/* Dil yalnız qonaqda (daxil olmuş müştəri Parametrlərdə dəyişir); sessiya yoxlanılarkən heç nə göstərilmir */}
          {status === 'guest' ? <LanguageSwitcher /> : null}
          {status === 'signedIn' ? (
            <Button asChild variant="ghost" size="icon" className="relative rounded-full [&_svg]:size-5">
              <Link
                to="/gift-cards"
                aria-label={
                  readyGifts > 0 ? t('profile:nav.giftCardsReady', { count: readyGifts }) : t('profile:nav.giftCards')
                }
              >
                <Gift aria-hidden="true" />
                {/* Hazır hədiyyələrin sayı: brend rəngli nişan, fondan ayrılsın deyə ağ halqa ilə */}
                {readyGifts > 0 ? (
                  <span
                    key={readyGifts}
                    className="animate-stamp-pop absolute top-1 right-0.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-primary px-1 text-[10.5px] leading-none font-extrabold text-primary-foreground ring-2 ring-background"
                    aria-hidden="true"
                  >
                    {readyGifts}
                  </span>
                ) : null}
              </Link>
            </Button>
          ) : null}
          {status === 'signedIn' ? (
            <Button asChild variant="ghost" size="icon" className="rounded-full [&_svg]:size-5">
              <Link to="/settings" aria-label={t('profile:nav.settings')}>
                <Settings aria-hidden="true" />
              </Link>
            </Button>
          ) : null}
        </div>
      </header>
      <main className="flex flex-1 flex-col gap-4 pt-2">
        {accountDeleted ? <Alert tone="success">{t('profile:settings.delete.done')}</Alert> : null}
        {consentGate ? <ConsentPage pending={pendingConsents} /> : <Outlet />}
      </main>
      <Footer className="pt-6" />
    </div>
  );
}
