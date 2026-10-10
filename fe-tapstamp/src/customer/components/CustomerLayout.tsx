import { useQuery } from '@tanstack/react-query';
import { Gift, Settings, Stamp } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { applyBrandColor } from '@/shared/lib/brand';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
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
  const accountDeleted = Boolean((location.state as { accountDeleted?: boolean } | null)?.accountDeleted);


  // Şirkətin brend rəngi tətbiqin əsas rəngi olur
  const brandColor = company.data?.brandColor;
  useEffect(() => applyBrandColor(brandColor), [brandColor]);

  useEffect(() => {
    document.title = companyName ? `${companyName} · ${t('common:app.loyaltyCard')}` : t('common:app.loyaltyCard');
  }, [companyName, t, i18n.language]);

  // Yeni versiyalı sənədlərə razılıq verilməyibsə — əvvəlcə razılıq (hüquqi sənədlər açıq qalır)
  const pendingConsents = me?.pendingConsents ?? [];
  const consentGate = status === 'signedIn' && pendingConsents.length > 0 && !location.pathname.startsWith('/legal/');

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <header className="sticky top-0 z-10 -mx-4 flex items-center justify-between gap-3 bg-background/85 px-4 py-3 backdrop-blur">
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
            <Button asChild variant="ghost" size="icon">
              <Link to="/gift-cards" aria-label={t('profile:nav.giftCards')}>
                <Gift aria-hidden="true" />
              </Link>
            </Button>
          ) : null}
          {status === 'signedIn' ? (
            <Button asChild variant="ghost" size="icon">
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
    </div>
  );
}
