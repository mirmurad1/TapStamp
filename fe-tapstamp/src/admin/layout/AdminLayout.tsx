import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { LayoutGrid, Lock, Menu, Nfc, ShieldCheck, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useLocation } from 'react-router';

import type { Language } from '@/shared/i18n/languages';
import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { applyBrandColor, applyIconVersion } from '@/shared/lib/brand';
import { cn } from '@/shared/lib/cn';
import { usePlan } from '@/shared/plan/plan';
import { Footer } from '@/shared/ui/Footer';
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher';

import { accountApi, contextApi, FEEDBACK_SUMMARY_KEY, feedbackApi, simulatorApi } from '../api/adminApi';
import { useAdminAuth, useMe } from '../auth/AdminAuthProvider';
import { sessionKeys } from '../auth/session';
import { StepUpProvider } from '../auth/StepUpProvider';
import { hasAnyRole, NAV_GROUPS, navigationFor, type NavItem } from './navigation';
import { UserMenu } from './UserMenu';
import { SIMULATOR_TAGS_KEY } from '../pages/dev/simulatorKeys';

/**
 * Admin panelinin çərçivəsi: sol menyu (rollara görə, qruplarla), şirkətin loqosu və brend rəngi; telefonda menyu
 * soldan açılır. Dil və istifadəçi menyusu menyunun altındadır.
 */
export function AdminLayout() {
  const { t } = useTranslation('admin');
  const { scope, realm } = useAdminAuth();
  const me = useMe();
  const queryClient = useQueryClient();
  // Dev simulyatoru yalnız mövcud olduqda (dev mühiti) menyuda görünür; prod-da endpoint 403 qaytarır
  const simulator = useQuery({
    queryKey: SIMULATOR_TAGS_KEY,
    queryFn: simulatorApi.tags,
    enabled: scope === 'TENANT',
    retry: false,
    staleTime: 60_000,
  });
  const plan = usePlan(scope === 'TENANT');
  const features = plan.data?.features;
  // Həll olunmamış pis rəylərin sayı — menyuda bildiriş sayğacı (yalnız sahib və imkan varsa)
  const feedbackAlerts = useQuery({
    queryKey: FEEDBACK_SUMMARY_KEY,
    queryFn: () => feedbackApi.summary(),
    enabled: scope === 'TENANT' && Boolean(features?.includes('FEEDBACK')) && hasAnyRole(me, ['COMPANY_OWNER']),
    refetchInterval: 60_000,
  });
  const unresolvedBad = feedbackAlerts.data?.unresolvedBad ?? 0;
  const items: NavItem[] = [
    ...navigationFor(scope, me),
    ...(simulator.isSuccess ? [{ section: 'simulator' as const, to: '/dev/simulator', icon: Nfc, roles: [], group: 'manage' as const }] : []),
  ];
  const title = scope === 'PLATFORM' ? t('shell.platformTitle') : t('shell.title');

  // Dil seçimi profilə yazılır: növbəti girişdə və digər cihazlarda da eyni dil açılır
  const saveLanguage = useMutation({
    mutationFn: (lang: Language) => accountApi.updateMe(realm, { preferredLanguage: lang }),
    onSuccess: (updated) => queryClient.setQueryData(sessionKeys.me, updated),
  });

  useEffect(() => {
    document.title = title;
  }, [title]);

  // Şirkətin brend rəngi admin panelinə də tətbiq olunur (müştəri tətbiqi ilə eyni)
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company, enabled: scope === 'TENANT' });
  const brandColor = company.data?.brandColor;
  useEffect(() => applyBrandColor(brandColor), [brandColor]);
  // Ana ekran ikonu (iPhone): loqo və ya rəng dəyişəndə telefon köhnə ikonu keşdən götürməsin
  const logoVersionUrl = company.data?.logoUrl;
  useEffect(() => applyIconVersion(brandColor, logoVersionUrl), [brandColor, logoVersionUrl]);

  // Telefonda menyu: səhifə dəyişəndə bağlanır
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setMenuOpen(false), [location.pathname]);

  const Icon = scope === 'PLATFORM' ? ShieldCheck : LayoutGrid;
  const groups = NAV_GROUPS.map((group) => ({ group, items: items.filter((item) => item.group === group) })).filter(
    (entry) => entry.items.length > 0,
  );
  const companyName = resolveText(company.data?.name, currentLanguage());
  const logoUrl = company.data?.logoUrl;

  // Şirkətin loqosu və adı (platformada — ümumi ikon və başlıq)
  const brand = (
    <div className="flex min-w-0 items-center gap-3">
      {scope === 'TENANT' && logoUrl ? (
        <img src={logoUrl} alt="" className="size-9 shrink-0 rounded-full object-cover ring-1 ring-border" />
      ) : (
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
          <Icon className="size-5" aria-hidden="true" />
        </span>
      )}
      <span className="min-w-0">
        <span className="block truncate font-display text-base font-semibold leading-tight">
          {scope === 'TENANT' && companyName ? companyName : title}
        </span>
        {scope === 'TENANT' && companyName ? (
          <span className="block text-xs text-muted-foreground">{title}</span>
        ) : null}
      </span>
    </div>
  );

  return (
    <StepUpProvider>
      <div className="flex min-h-dvh flex-col md:flex-row">
        {/* Telefon: yuxarıda menyu düyməsi, şirkət və profil; menyu soldan açılır */}
        <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-border bg-card px-4 py-2.5 md:hidden">
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            className="grid size-10 shrink-0 place-items-center rounded-lg hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={t('shell.openMenu')}
            aria-expanded={menuOpen}
          >
            <Menu className="size-5" aria-hidden="true" />
            {unresolvedBad > 0 ? <span className="absolute ml-6 -mt-6 size-2.5 rounded-full bg-destructive" aria-hidden="true" /> : null}
          </button>
          {brand}
          <div className="ml-auto">
            <UserMenu />
          </div>
        </header>
        {menuOpen ? (
          <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={() => setMenuOpen(false)} aria-hidden="true" />
        ) : null}
        <aside
          className={cn(
            'fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-border bg-card transition-transform motion-reduce:transition-none',
            'md:sticky md:top-0 md:z-auto md:h-dvh md:w-64 md:translate-x-0',
            menuOpen ? 'translate-x-0' : '-translate-x-full',
          )}
          aria-label={t('shell.mainNav')}
        >
          <div className="flex items-center gap-2 px-4 py-4">
            {brand}
            <button
              type="button"
              onClick={() => setMenuOpen(false)}
              className="ml-auto grid size-9 shrink-0 place-items-center rounded-lg hover:bg-muted md:hidden"
              aria-label={t('shell.closeMenu')}
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </div>
          <nav className="flex-1 overflow-y-auto px-3 pb-3">
            {groups.map(({ group, items: groupItems }) => (
              <div key={group} className="mt-2 first:mt-0">
                {groups.length > 1 ? (
                  <p className="px-3 pt-3 pb-1 text-xs font-medium text-muted-foreground">{t(`nav.groups.${group}`)}</p>
                ) : null}
                <div className="flex flex-col gap-0.5">
                  {groupItems.map(({ section, to, icon: ItemIcon, feature, addon }) => (
                    <NavLink
                      key={section}
                      to={to}
                      end={to === '/'}
                      className={({ isActive }) =>
                        cn(
                          'flex min-h-9 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                          isActive
                            ? 'bg-primary/10 text-primary'
                            : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                        )
                      }
                    >
                      <ItemIcon className="size-4 shrink-0" aria-hidden="true" />
                      <span className="truncate">{t(`nav.${section}`)}</span>
                      {section === 'feedback' && unresolvedBad > 0 ? (
                        <span
                          className="ml-auto grid h-5 min-w-5 place-items-center rounded-full bg-destructive px-1.5 text-[11px] font-bold text-white"
                          aria-label={t('feedback.unresolvedAlert', { count: unresolvedBad })}
                        >
                          {unresolvedBad}
                        </span>
                      ) : null}
                      {(feature && features && !features.includes(feature))
                      || (addon && plan.data && !(plan.data.addons ?? []).includes(addon)) ? (
                        <Lock className="ml-auto size-3.5 shrink-0" aria-label={t('plan.locked')} />
                      ) : null}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
          {/* Dil və profil menyunun altında — yuxarıda ayrıca boş zolaq qalmır */}
          <div className="flex flex-col gap-2 border-t border-border p-3">
            <LanguageSwitcher className="flex w-full [&>button]:flex-1" onSelect={(lang) => saveLanguage.mutate(lang)} />
            <div className="hidden md:block">
              <UserMenu className="w-full" side="top" />
            </div>
          </div>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-8 md:py-8">
            <Outlet />
          </main>
          <Footer className="border-t border-border" />
        </div>
      </div>
    </StepUpProvider>
  );
}
