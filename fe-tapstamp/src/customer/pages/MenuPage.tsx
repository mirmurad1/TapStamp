import { useQuery } from '@tanstack/react-query';
import { BookOpen, ChevronRight, Gift, Search, X } from 'lucide-react';
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
  const [query, setQuery] = useState('');
  const titleKey = (catalog.data?.title ?? 'MENU') as 'MENU' | 'SERVICES' | 'PRICES';
  const title = t(`menu.titles.${titleKey}`);
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
  // Axtarış: ad və izah üzrə, hər üç dildə (müştəri başqa dildə yazsa da tapılsın)
  const needle = normalized(query);
  const matches = (item: Item) =>
    [...Object.values(item.name ?? {}), ...Object.values(item.description ?? {})].some((text) => normalized(text).includes(needle));
  const shown = needle
    ? categories
        .map((category) => ({ ...category, items: (category.items ?? []).filter(matches) }))
        .filter((category) => category.items.length > 0)
    : categories;
  return (
    <>
      <h1 className="pt-2 font-display text-3xl font-extrabold leading-tight tracking-tight">{title}</h1>
      <LoyaltyBanner />
      <SearchField value={query} onChange={setQuery} placeholder={t(`menu.searchIn.${titleKey}`)} />
      {needle ? null : <CategoryTabs categories={categories} />}
      {needle && shown.length === 0 ? (
        <p className="py-10 text-center text-muted-foreground" role="status">{t('menu.searchEmpty', { query: query.trim() })}</p>
      ) : null}
      {shown.map((category) => (
        <section key={category.id} id={`c-${category.id}`} className="flex scroll-mt-32 flex-col gap-3" aria-labelledby={`h-${category.id}`}>
          <h2 id={`h-${category.id}`} className="flex items-baseline gap-2 px-0.5 pt-3">
            <span className="text-xl font-extrabold tracking-tight">{resolveText(category.name, lang)}</span>
            <span className="text-sm font-medium text-muted-foreground tabular-nums">{category.items?.length ?? 0}</span>
          </h2>
          <ul className="divide-y divide-border overflow-hidden rounded-3xl bg-card shadow-[0_1px_2px_rgb(0_0_0/0.04),0_8px_24px_-12px_rgb(0_0_0/0.12)]">
            {(category.items ?? []).map((item) => (
              <ItemCard key={item.id} item={item} reward={isReward(item)} />
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}

/** Axtarış sahəsi: solda ikon, yazı olanda sağda təmizləmə düyməsi. */
function SearchField({ value, onChange, placeholder }: {
  value: string;
  onChange: (next: string) => void;
  placeholder: string;
}) {
  const { t } = useTranslation('profile');
  return (
    <div className="relative">
      <Search className="pointer-events-none absolute top-1/2 left-4 size-5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        enterKeyHint="search"
        className="h-12 w-full appearance-none rounded-full border border-border bg-card pr-12 pl-12 text-base shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-search-cancel-button]:hidden"
      />
      {value ? (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label={t('menu.searchClear')}
          className="absolute top-1/2 right-2 grid size-9 -translate-y-1/2 place-items-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}

/** Kateqoriya düymələri: üfüqi sürüşür; toxunanda həmin hissəyə keçir, aktiv olan scroll-a görə dəyişir. */
function CategoryTabs({ categories }: { categories: Category[] }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const [active, setActive] = useState(categories[0]?.id);
  const strip = useRef<HTMLDivElement>(null);
  // Tab basılandan sonra səhifə sürüşərkən (və ya səhifə qısadır, sürüşə bilmir) seçim scroll-a görə dəyişməsin
  const pinnedUntil = useRef(0);
  useEffect(() => {
    const onScroll = () => {
      if (Date.now() < pinnedUntil.current) {
        return;
      }
      let current = categories[0]?.id;
      for (const category of categories) {
        const section = document.getElementById(`c-${category.id}`);
        if (section && section.getBoundingClientRect().top <= SCROLL_OFFSET + 8) {
          current = category.id;
        }
      }
      // Səhifənin sonunda — sonuncu kateqoriya (qısa bölmə yuxarıya çata bilmir); səhifə sürüşmürsə yox
      const root = document.documentElement;
      if (window.scrollY > 0 && root.scrollHeight > window.innerHeight + 4
          && window.innerHeight + window.scrollY >= root.scrollHeight - 4) {
        current = categories[categories.length - 1]?.id;
      }
      setActive(current);
    };
    // İstifadəçi özü sürüşdürəndə tabın "bərkidilməsi" dərhal götürülür
    const release = () => {
      pinnedUntil.current = 0;
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('touchstart', release, { passive: true });
    window.addEventListener('wheel', release, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('touchstart', release);
      window.removeEventListener('wheel', release);
    };
  }, [categories]);
  // Aktiv tab zolaqda ortaya gəlsin — yalnız zolaq üfüqi sürüşür (scrollIntoView bütün səhifəni də tərpədir)
  useEffect(() => {
    const container = strip.current;
    const tab = container?.querySelector<HTMLElement>(`[data-id="${active}"]`);
    if (container && tab) {
      container.scrollTo({ left: tab.offsetLeft - (container.clientWidth - tab.offsetWidth) / 2, behavior: 'smooth' });
    }
  }, [active]);
  if (categories.length < 2) {
    return null;
  }
  const go = (id: string | undefined) => {
    setActive(id);
    pinnedUntil.current = Date.now() + 1000;
    const section = document.getElementById(`c-${id}`);
    if (section) {
      window.scrollTo({ top: section.getBoundingClientRect().top + window.scrollY - SCROLL_OFFSET + 8, behavior: 'smooth' });
    }
  };
  return (
    // Alt xətli tablar: aktiv olan tünd mətn və brend rəngli qalın xətt; zolağın altında incə ayırıcı
    <nav className="sticky top-16 z-[5] -mx-4 border-b border-border bg-background px-4" aria-label={t('menu.categories')}>
      {/* Yalnız üfüqi sürüşür; alt xətt zolağın içindədir — şaquli sürüşmə yaranmır */}
      <div ref={strip} className="flex gap-6 overflow-x-auto overflow-y-hidden overscroll-x-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {categories.map((category) => (
          <button
            key={category.id}
            type="button"
            data-id={category.id}
            onClick={() => go(category.id)}
            aria-current={active === category.id ? 'true' : undefined}
            className={cn(
              'relative shrink-0 py-3.5 text-[15px] font-semibold whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              'after:absolute after:inset-x-0 after:bottom-0 after:h-[3px] after:rounded-full',
              active === category.id ? 'text-foreground after:bg-primary' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {resolveText(category.name, lang)}
          </button>
        ))}
      </div>
    </nav>
  );
}

/**
 * Məhsulun şəkli: kiçik variant (192 px JPEG, ~10–15 KB) yüklənir. Yüklənənə qədər yerində boz fon qalır ki, siyahı
 * tullanmasın; şəkil gələndə yumşaq görünür. Ölçülər əvvəlcədən verilib — brauzer yeri ayırır.
 */
function ItemImage({ src }: { src: string }) {
  const [loaded, setLoaded] = useState(false);
  return (
    <span className="size-[88px] shrink-0 self-center overflow-hidden rounded-2xl bg-muted">
      <img
        src={src}
        alt=""
        width={88}
        height={88}
        loading="lazy"
        decoding="async"
        onLoad={() => setLoaded(true)}
        className={cn(
          'size-full object-cover transition-opacity duration-300 motion-reduce:transition-none',
          loaded ? 'opacity-100' : 'opacity-0',
        )}
      />
    </span>
  );
}

function ItemCard({ item, reward }: { item: Item; reward: boolean }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const soldOut = item.available === false;
  return (
    <li className={cn('flex gap-4 px-4 py-4', soldOut && 'opacity-55')}>
      <div className="min-w-0 flex-1">
        <p className="text-[16px] font-semibold leading-snug break-words">{resolveText(item.name, lang)}</p>
        {item.description ? (
          <p className="mt-1 line-clamp-2 text-sm leading-snug text-muted-foreground">{resolveText(item.description, lang)}</p>
        ) : null}
        <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          {item.price != null ? (
            <span className="text-[15px] font-extrabold text-primary tabular-nums">{formatCurrency(Number(item.price), lang)}</span>
          ) : null}
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
      {item.thumbUrl ?? item.imageUrl ? <ItemImage src={(item.thumbUrl ?? item.imageUrl) as string} /> : null}
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

/**
 * Menyunun başında loyallıq banneri: açıq kart, solda irəliləyiş halqası (müştəriyə — möhürləri "4/5", qonağa —
 * hədiyyə ikonu), ortada qalan möhürlər və hədiyyə, sağda düymə. Rəng yalnız halqada və düymədədir — menyu sakit qalır.
 * Qeydiyyatdan keçmiş müştəri toxunanda möhür kartına, qonaq qeydiyyata keçir.
 */
function LoyaltyBanner() {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const { status, me } = useCustomerSession();
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  if (status === 'loading') {
    return null;
  }
  const card = me?.card;
  const signedIn = status === 'signedIn' && Boolean(card);
  const required = (signedIn ? card?.stampsRequired : company.data?.rewardRule?.stampsRequired) ?? 0;
  const current = signedIn ? Math.min(card?.currentStamps ?? 0, required) : 0;
  const reward = resolveText(signedIn ? card?.rewardName : company.data?.rewardRule?.rewardName, lang);
  return (
    <Link
      to={signedIn ? '/' : '/login'}
      className="flex items-center gap-3.5 rounded-3xl border border-primary/15 bg-[color-mix(in_srgb,var(--primary)_7%,var(--card))] p-3.5 pr-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <ProgressRing value={current} total={required} signedIn={signedIn} />
      <span className="min-w-0 flex-1">
        <span className="block font-bold leading-snug">
          {signedIn ? t('card.progress', { count: Math.max(required - current, 0) }) : t('menu.joinTitle')}
        </span>
        {reward ? (
          <span className="mt-0.5 line-clamp-2 text-sm leading-snug text-muted-foreground">{t('menu.joinReward', { reward })}</span>
        ) : null}
      </span>
      <span className="shrink-0 rounded-full bg-primary px-3.5 py-2 text-sm font-bold text-primary-foreground">
        {signedIn ? t('menu.stubCard') : t('menu.stubJoin')}
      </span>
    </Link>
  );
}

/** İrəliləyiş halqası: brend rəngli qövs möhürlərin payı qədər; ortada "4/5" və ya (qonağa) hədiyyə ikonu. */
function ProgressRing({ value, total, signedIn }: { value: number; total: number; signedIn: boolean }) {
  const radius = 23;
  const circumference = 2 * Math.PI * radius;
  const share = total > 0 ? value / total : 0;
  return (
    <span className="relative grid size-14 shrink-0 place-items-center" aria-hidden="true">
      <svg viewBox="0 0 56 56" className="absolute inset-0 size-full -rotate-90">
        <circle cx="28" cy="28" r={radius} fill="none" strokeWidth="5" className="stroke-primary/15" />
        {share > 0 ? (
          <circle cx="28" cy="28" r={radius} fill="none" strokeWidth="5" strokeLinecap="round" className="stroke-primary"
            strokeDasharray={circumference} strokeDashoffset={circumference * (1 - share)} />
        ) : null}
      </svg>
      {signedIn ? (
        <span className="text-base leading-none font-extrabold tabular-nums">
          {value}
          <span className="text-[11px] font-bold text-muted-foreground">/{total}</span>
        </span>
      ) : (
        <Gift className="size-5 text-primary" />
      )}
    </span>
  );
}
