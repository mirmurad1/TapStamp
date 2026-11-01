import { useQuery } from '@tanstack/react-query';
import { BookOpen, ChevronRight, Gift, Stamp } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatCurrency } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';

import { CATALOG_KEY, publicApi, type PublicCatalog } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { BonusRulesBanner } from '../components/BonusRulesBanner';

type Category = NonNullable<PublicCatalog['categories']>[number];
type Item = NonNullable<Category['items']>[number];

/** Kateqoriya düymələri yapışqan zolaqdadır — hissəyə keçəndə başlıq onun altında qalmasın. */
const SCROLL_OFFSET = 132;

/** Mətni müqayisə üçün sadələşdirir (hədiyyə adı ilə məhsul adını tutuşdurmaq). */
function normalized(value: string | undefined): string {
  return (value ?? '').trim().toLocaleLowerCase();
}

/**
 * Menyu və ya xidmətlər (əlavə CATALOG): giriş tələb etmir, stol üstündəki QR ilə açılır ({@code ?branch=} — filial).
 * Kateqoriyalar yuxarıdakı yapışqan düymələrlə keçilir; möhür kartının hədiyyəsi olan məhsul nişanlanır, aşağıda
 * qonağa loyallıq kartına qoşulmaq təklif olunur.
 */
export function MenuPage() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const [params] = useSearchParams();
  const branchId = params.get('branch') ?? undefined;
  const catalog = useQuery({
    queryKey: [...CATALOG_KEY, branchId ?? ''],
    queryFn: () => publicApi.catalog(branchId),
    staleTime: 60_000,
  });
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const categories = catalog.data?.categories ?? [];
  const title = t(`menu.titles.${(catalog.data?.title ?? 'MENU') as 'MENU' | 'SERVICES' | 'PRICES'}`);
  // Hədiyyə: məhsulun adı möhür kartının hədiyyəsinin adı ilə eynidirsə (istənilən dildə)
  const rewardNames = new Set(Object.values(company.data?.rewardRule?.rewardName ?? {}).map(normalized).filter(Boolean));
  const isReward = (item: Item) => Object.values(item.name ?? {}).some((name) => rewardNames.has(normalized(name)));

  // Brauzer tabı: "Şirkət · Menyu" (layout menyu səhifəsində öz başlığını qoymur)
  const companyName = resolveText(company.data?.name, lang);
  useEffect(() => {
    document.title = companyName ? `${companyName} · ${title}` : title;
  }, [companyName, title]);

  if (catalog.isPending) {
    return <p className="py-16 text-center text-muted-foreground" role="status">{t('menu.loading')}</p>;
  }
  if (!catalog.data?.enabled || categories.length === 0) {
    return (
      <section className="flex flex-col items-center gap-3 py-16 text-center">
        <span className="grid size-14 place-items-center rounded-2xl bg-primary/10 text-primary">
          <BookOpen className="size-7" aria-hidden="true" />
        </span>
        <h1 className="text-xl font-bold">{t('menu.unavailable')}</h1>
        <Button asChild variant="outline">
          <Link to="/">{t('menu.backHome')}</Link>
        </Button>
      </section>
    );
  }
  return (
    <>
      <h1 className="pt-2 font-display text-3xl font-extrabold leading-tight tracking-tight">{title}</h1>
      <BonusRulesBanner />
      <CategoryTabs categories={categories} />
      {categories.map((category) => (
        <section key={category.id} id={`c-${category.id}`} className="flex scroll-mt-32 flex-col gap-2" aria-labelledby={`h-${category.id}`}>
          <h2 id={`h-${category.id}`} className="px-0.5 pt-2 text-lg font-bold">{resolveText(category.name, lang)}</h2>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
            {(category.items ?? []).map((item) => (
              <ItemCard key={item.id} item={item} reward={isReward(item)} />
            ))}
          </ul>
        </section>
      ))}
      <JoinLoyalty />
    </>
  );
}

/** Kateqoriya düymələri: üfüqi sürüşür; toxunanda həmin hissəyə keçir, aktiv olan scroll-a görə dəyişir. */
function CategoryTabs({ categories }: { categories: Category[] }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const [active, setActive] = useState(categories[0]?.id);
  const strip = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onScroll = () => {
      let current = categories[0]?.id;
      for (const category of categories) {
        const section = document.getElementById(`c-${category.id}`);
        if (section && section.getBoundingClientRect().top <= SCROLL_OFFSET + 8) {
          current = category.id;
        }
      }
      // Səhifənin sonunda — sonuncu kateqoriya (qısa bölmə yuxarıya çata bilmir)
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) {
        current = categories[categories.length - 1]?.id;
      }
      setActive(current);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [categories]);
  // Aktiv düymə zolaqda görünsün
  useEffect(() => {
    strip.current?.querySelector<HTMLElement>(`[data-id="${active}"]`)?.scrollIntoView({ block: 'nearest', inline: 'center' });
  }, [active]);
  if (categories.length < 2) {
    return null;
  }
  const go = (id: string | undefined) => {
    const section = document.getElementById(`c-${id}`);
    if (section) {
      window.scrollTo({ top: section.getBoundingClientRect().top + window.scrollY - SCROLL_OFFSET + 8, behavior: 'smooth' });
    }
  };
  return (
    <nav className="sticky top-16 z-[5] -mx-4 bg-background px-4 py-2" aria-label={t('menu.categories')}>
      <div ref={strip} className="flex gap-2 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {categories.map((category) => (
          <button
            key={category.id}
            type="button"
            data-id={category.id}
            onClick={() => go(category.id)}
            aria-current={active === category.id ? 'true' : undefined}
            className={cn(
              // Rəng keçidi yoxdur: brend rəngi yüklənəndə standart rəngdən animasiya ilə keçib "yanıb-sönməsin"
              'shrink-0 rounded-full border px-4 py-2 text-sm font-semibold whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              active === category.id ? 'border-primary bg-primary text-primary-foreground' : 'border-border bg-card hover:bg-muted',
            )}
          >
            {resolveText(category.name, lang)}
          </button>
        ))}
      </div>
    </nav>
  );
}

function ItemCard({ item, reward }: { item: Item; reward: boolean }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const soldOut = item.available === false;
  return (
    <li className={cn('flex gap-3 p-4', soldOut && 'opacity-55')}>
      <div className="min-w-0 flex-1">
        <p className="font-semibold leading-snug break-words">{resolveText(item.name, lang)}</p>
        {item.description ? (
          <p className="mt-0.5 line-clamp-3 text-sm text-muted-foreground">{resolveText(item.description, lang)}</p>
        ) : null}
        <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          {item.price != null ? <span className="font-bold tabular-nums">{formatCurrency(Number(item.price), lang)}</span> : null}
          {item.durationMinutes ? <span className="text-muted-foreground">{t('menu.minutes', { count: item.durationMinutes })}</span> : null}
          {soldOut ? (
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-semibold text-muted-foreground">{t('menu.soldOut')}</span>
          ) : null}
          {reward ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
              <Gift className="size-3.5" aria-hidden="true" />
              {t('menu.reward')}
            </span>
          ) : null}
        </p>
      </div>
      {item.imageUrl ? (
        <img src={item.imageUrl} alt="" loading="lazy" className="size-24 shrink-0 rounded-xl object-cover" />
      ) : null}
    </li>
  );
}

/**
 * Ana səhifədə menyuya keçid (təkliflər siyahısında sətir): yalnız menyu açıq və boş deyilsə. Ad — bölmənin adı
 * (Menyu, Xidmətlər və ya Qiymətlər).
 */
export function MenuRow({ className }: { className?: string }) {
  const { t } = useTranslation('profile');
  const catalog = useQuery({ queryKey: [...CATALOG_KEY, ''], queryFn: () => publicApi.catalog(), staleTime: 60_000 });
  if (!catalog.data?.enabled || (catalog.data.categories ?? []).length === 0) {
    return null;
  }
  const title = (catalog.data.title ?? 'MENU') as 'MENU' | 'SERVICES' | 'PRICES';
  return (
    <Link
      to="/menu"
      className={cn(
        'flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:outline-none',
        className,
      )}
    >
      <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary [&_svg]:size-5" aria-hidden="true">
        <BookOpen />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{t(`menu.titles.${title}`)}</span>
        <span className="block text-[13px] leading-snug text-muted-foreground">{t(`menu.rowHint.${title}`)}</span>
      </span>
      <ChevronRight className="size-4 shrink-0 text-muted-foreground/60" aria-hidden="true" />
    </Link>
  );
}

/** Menyunun sonunda: qonağa — möhür kartına qoşulmaq, daxil olmuş müştəriyə — kartına keçid. */
function JoinLoyalty() {
  const { t } = useTranslation('profile');
  const { status, me } = useCustomerSession();
  const card = me?.card;
  const signedIn = status === 'signedIn' && card;
  return (
    <Link
      to={signedIn ? '/' : '/login'}
      className="mt-2 flex items-center gap-3 rounded-2xl bg-primary p-4 text-primary-foreground shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-white/20">
        <Stamp className="size-6" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-bold">
          {signedIn ? t('menu.cardTitle', { current: card.currentStamps ?? 0, total: card.stampsRequired ?? 0 }) : t('menu.joinTitle')}
        </span>
        <span className="block text-sm opacity-90">{signedIn ? t('menu.cardHint') : t('menu.joinHint')}</span>
      </span>
      <ChevronRight className="size-5 shrink-0" aria-hidden="true" />
    </Link>
  );
}
