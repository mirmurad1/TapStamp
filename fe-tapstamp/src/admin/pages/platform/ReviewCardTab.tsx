import { useQuery } from '@tanstack/react-query';
import { BookOpen, Check, ChevronDown, Copy, Download, Printer, Star, TriangleAlert } from 'lucide-react';
import QRCode from 'qrcode';
import { useEffect, useState, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatNumber } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/shared/ui/dropdown-menu';
import { Select } from '@/shared/ui/input';
import { QrCode } from '@/shared/ui/QrCode';

import { platformApi, type ReviewCard } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { downloadBlob } from '../../lib/download';
import { TenantLogo } from './TenantLogo';

type Size = 'A6' | 'A5' | 'A4';
type Languages = 'az' | 'az-ru' | 'az-en';
type MenuTitle = 'MENU' | 'SERVICES' | 'PRICES';
const SIZES: readonly Size[] = ['A6', 'A5', 'A4'];
const LANGUAGES: readonly Languages[] = ['az', 'az-ru', 'az-en'];

/** Kartın əsas şrift ölçüsü (bütün ölçülər em ilə) — A6-da ~14 px; A5 √2 dəfə böyükdür. */
const BASE_FONT = { A6: '3.7mm', A5: '5.23mm', A4: '3.7mm' } as const;
/** Önizləmədə eyni nisbət konteynerin eninə görə: 3.7 mm / 105 mm ≈ 3.524% en. */
const PREVIEW_FONT = '3.524cqw';

/** Kartda nə olsun: yalnız Google rəyi, yalnız menyu və ya hər ikisi. */
type Content = 'review' | 'menu' | 'both';
const CONTENTS: readonly Content[] = ['review', 'menu', 'both'];

interface CardProps {
  tenantId: string;
  companyName: string;
  branchName: string;
  brandColor: string;
  languages: Languages;
  /** Kartın əsas şrift ölçüsü; bütün ölçülər ona nisbətdədir. */
  fontSize: string;
  /** Filialın Google rəyi QR-ı (şirkətin domenindən yönləndirmə) — kartda rəy varsa. */
  review?: { url: string };
  /** Menyu QR-ı — kartda menyu varsa; {@code title} bölmənin adıdır (MENU, SERVICES, PRICES). */
  menu?: { url: string; title: MenuTitle };
}

const QR_FRAME = 'border-[0.14em] border-[color-mix(in_srgb,var(--brand)_18%,#fff)]';

/**
 * Çap olunan kartın üzü (A6 nisbəti, 105 × 148 mm). Bütün ölçülər em-dədir: eyni kart önizləmədə, A6 və A5 çapında
 * yalnız əsas şrift ölçüsü ilə böyüyür. Mətnlər seçilmiş dil(lər)dədir, panelin dilindən asılı deyil.
 * Üç növ: yalnız Google rəyi (ulduzlar, bir QR), yalnız menyu (kitab nişanı, bir QR), hər ikisi (salam, iki QR).
 */
function ReviewCardFace({ tenantId, companyName, branchName, brandColor, languages, fontSize, review, menu }: CardProps) {
  const { t } = useTranslation('admin');
  const second = languages === 'az' ? undefined : languages.slice(3);
  const both = Boolean(review && menu);
  // Kartın mətnləri seçilmiş dildə (panelin dilindən asılı deyil)
  const texts = (lng: string) => ({
    title: both
      ? t('platform.reviewCard.card.welcome', { lng })
      : menu
        ? t(`platform.reviewCard.card.menuTitle.${menu.title}`, { lng })
        : t('platform.reviewCard.card.title', { lng }),
    subtitle: menu ? t('platform.reviewCard.card.menuSubtitle', { lng }) : t('platform.reviewCard.card.subtitle', { lng }),
    scan: both ? t('platform.reviewCard.card.scanEither', { lng }) : t('platform.reviewCard.card.scan', { lng }),
    thanks: t('platform.reviewCard.card.thanks', { lng }),
    review: t('platform.reviewCard.card.review', { lng }),
    menu: menu ? t(`platform.reviewCard.card.menu.${menu.title}`, { lng }) : '',
  });
  const az = texts('az');
  const other = second ? texts(second) : undefined;
  const single = review ?? menu;
  return (
    <div
      className="flex size-full flex-col items-center overflow-hidden bg-white text-center text-[#151a2d] [-webkit-print-color-adjust:exact] [print-color-adjust:exact]"
      style={{ fontSize, '--brand': brandColor } as CSSProperties}
    >
      <div
        className={cn(
          'flex w-full flex-col items-center gap-[0.35em] bg-[linear-gradient(150deg,var(--brand),color-mix(in_srgb,var(--brand)_68%,#000))] px-[1.4em] pt-[1.4em] text-white',
          // Nişan (ulduzlar və ya kitab) zolağın altına girir; iki QR-lı kartda nişan yoxdur, zolaq qısadır
          both ? 'pb-[1.4em]' : 'pb-[2.1em]',
        )}
      >
        <TenantLogo
          tenantId={tenantId}
          name={companyName}
          lang="az"
          className="size-[2.6em] rounded-[0.7em] bg-white text-[1.4em] text-[var(--brand)] shadow-[0_0_0_0.2em_rgb(255_255_255/0.25)]"
        />
        <p className="mt-[0.2em] text-[1.55em] leading-tight font-extrabold tracking-tight">{companyName}</p>
        <p className="text-[0.9em] opacity-85">{branchName}</p>
      </div>
      {both ? null : (
        <div className="-mt-[1.35em] flex gap-[0.15em] rounded-full bg-white px-[0.9em] py-[0.5em] shadow-[0_0.4em_1em_-0.4em_rgb(0_0_0/0.3)]">
          {menu ? (
            <BookOpen className="size-[1.9em] text-[var(--brand)]" aria-hidden="true" />
          ) : (
            [0, 1, 2, 3, 4].map((index) => (
              <Star key={index} className="size-[1.9em] fill-amber-400 text-amber-400" aria-hidden="true" />
            ))
          )}
        </div>
      )}
      <p className={cn('px-[0.8em] text-[1.7em] leading-[1.12] font-extrabold tracking-tight text-balance', both ? 'mt-[0.75em]' : 'mt-[0.85em]')}>
        {az.title}
      </p>
      {other ? <p className="mt-[0.2em] px-[1em] text-[1.15em] leading-tight font-bold text-[#5b6178]">{other.title}</p> : null}
      {both && review && menu ? (
        // İki QR: Google rəyi və menyu — hər birinin altında nəyə apardığı yazılır
        <div className="mt-[1em] grid w-full grid-cols-2 gap-[1em] px-[1.6em]">
          {[
            { url: review.url, icon: <Star className="size-[1em] fill-amber-400 text-amber-400" aria-hidden="true" />, label: az.review, second: other?.review },
            { url: menu.url, icon: <BookOpen className="size-[1em] text-[var(--brand)]" aria-hidden="true" />, label: az.menu, second: other?.menu },
          ].map((item) => (
            <div key={item.url} className="flex min-w-0 flex-col items-center">
              <div className={cn('aspect-square w-full rounded-[0.9em] p-[0.4em]', QR_FRAME)}>
                <QrCode value={item.url} label={item.label} className="size-full" />
              </div>
              <p className="mt-[0.45em] flex items-center gap-[0.3em] text-[0.88em] leading-tight font-bold">
                {item.icon}
                {item.label}
              </p>
              {item.second ? <p className="text-[0.75em] leading-tight text-[#5b6178]">{item.second}</p> : null}
            </div>
          ))}
        </div>
      ) : single ? (
        <>
          <p className="mt-[0.5em] px-[2em] text-[0.92em] leading-snug text-[#5b6178]">{az.subtitle}</p>
          <div className={cn('mt-[0.9em] size-[11.5em] rounded-[1.1em] p-[0.5em]', QR_FRAME)}>
            <QrCode value={single.url} label={az.scan} className="size-full" />
          </div>
        </>
      ) : null}
      <p className="mt-[0.75em] px-[1.5em] text-[0.92em] leading-snug font-semibold text-[#3b3f55]">{az.scan}</p>
      {other ? <p className="px-[1.5em] text-[0.8em] leading-snug text-[#5b6178]">{other.scan}</p> : null}
      <p className="mt-auto pb-[1em] text-[0.78em] text-[#9aa0b4]">
        {az.thanks}
        {other ? ` · ${other.thanks}` : null}
      </p>
    </div>
  );
}

/** Fayl adı üçün: Azərbaycan hərfləri latın qarşılığına, qalanı tire ilə ("Nərimanov filialı" → "nerimanov-filiali"). */
function fileSlug(value: string): string {
  const map: Record<string, string> = { ə: 'e', ı: 'i', ö: 'o', ü: 'u', ş: 's', ç: 'c', ğ: 'g' };
  return value
    .toLocaleLowerCase('az')
    .replace(/[əıöüşçğ]/g, (letter) => map[letter] ?? letter)
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40) || 'filial';
}

/** QR kodun vektor faylı — stend hazırlayan öz dizaynında istifadə etsin. */
function qrSvg(value: string): string {
  const qr = QRCode.create(value, { errorCorrectionLevel: 'M' });
  const count = qr.modules.size;
  let path = '';
  for (let y = 0; y < count; y++) {
    for (let x = 0; x < count; x++) {
      if (qr.modules.get(x, y)) {
        path += `M${x + 4} ${y + 4}h1v1h-1z`;
      }
    }
  }
  const size = count + 8;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">`
    + `<rect width="${size}" height="${size}" fill="#ffffff"/><path d="${path}" fill="#000000"/></svg>`;
}

/**
 * Çap üçün səhifələr (yalnız çap zamanı görünür): A6 və A5 — bir kart bir səhifədə, A4 — 2 × 2 kart kəsmə xətləri ilə.
 */
function PrintPages({ size, card }: { size: Size; card: Omit<CardProps, 'fontSize'> }) {
  return createPortal(
    <div className="review-print-root">
      {size === 'A4' ? (
        <div className="grid h-[297mm] w-[210mm] grid-cols-2 grid-rows-2 [page:review-a4]">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="border-[0.2mm] border-dashed border-[#c7cbd6]">
              <ReviewCardFace {...card} fontSize={BASE_FONT.A4} />
            </div>
          ))}
        </div>
      ) : (
        <div className={cn(size === 'A6' ? 'h-[148mm] w-[105mm] [page:review-a6]' : 'h-[210mm] w-[148mm] [page:review-a5]')}>
          <ReviewCardFace {...card} fontSize={BASE_FONT[size]} />
        </div>
      )}
    </div>,
    document.body,
  );
}

/**
 * Platforma panelində şirkətin "Çap materialları" bölməsi: stend üçün QR kart hazırlanır və çap olunur — Google rəyi,
 * menyu və ya hər ikisi (menyu yalnız açıqdırsa).
 */
export function ReviewCardTab({ tenantId, companyName, slug }: { tenantId: string; companyName: string; slug: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const data = useQuery({ queryKey: ['platform-review-cards', tenantId], queryFn: () => platformApi.reviewCards(tenantId) });
  const cards = data.data?.cards ?? [];
  const [branchId, setBranchId] = useState('');
  const [size, setSize] = useState<Size>('A6');
  const [languages, setLanguages] = useState<Languages>('az');
  const [printing, setPrinting] = useState(false);
  const [content, setContent] = useState<Content>('review');
  const [copied, setCopied] = useState<'review' | 'menu'>();
  const selected: ReviewCard | undefined = cards.find((card) => card.branchId === branchId) ?? cards[0];

  // Çap: kartlar səhifəyə əlavə olunur, brauzerin çap pəncərəsi açılır, bağlananda təmizlənir.
  // Səhifə ölçüsü və sıfır kənar ayrıca CSS faylı ilə qoşulur (CSP inline <style>-a icazə vermir): brauzer kağız
  // ölçüsünü kartdan götürür və kənar olmadığı üçün yuxarı/aşağı başlıqları (tarix, səhifə adı, ünvan) yazmır.
  useEffect(() => {
    if (!printing) {
      return;
    }
    document.body.classList.add('printing-review');
    const done = () => setPrinting(false);
    window.addEventListener('afterprint', done);
    const page = document.createElement('link');
    page.rel = 'stylesheet';
    page.href = `/admin/print/review-${size.toLowerCase()}.css`;
    let timer: number | undefined;
    // Stil yüklənəndən sonra (loqo və şriftlər də hazır olsun deyə qısa gözləmə ilə) çap pəncərəsi açılır
    const open = () => {
      timer = window.setTimeout(() => window.print(), 150);
    };
    page.addEventListener('load', open, { once: true });
    page.addEventListener('error', open, { once: true });
    document.head.append(page);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('afterprint', done);
      document.body.classList.remove('printing-review');
      page.remove();
    };
  }, [printing, size]);

  if (data.isPending) {
    return <InlineLoading />;
  }
  if (data.error) {
    return <ApiErrorAlert error={data.error} />;
  }
  if (!selected) {
    return <p className="text-sm text-muted-foreground">{t('platform.reviewCard.noBranches')}</p>;
  }

  const branchName = resolveText(selected.name, 'az');
  const menuOpen = Boolean(data.data?.menuUrl);
  // Menyu bağlıdırsa yalnız Google rəyi qalır (seçim əvvəlcədən edilmiş olsa da)
  const shown: Content = menuOpen ? content : 'review';
  const menu = data.data?.menuUrl ? { url: data.data.menuUrl, title: (data.data.menuTitle ?? 'MENU') as MenuTitle } : undefined;
  const card = {
    tenantId,
    companyName,
    branchName,
    brandColor: data.data?.brandColor ?? '#6D28D9',
    languages,
    review: shown === 'menu' ? undefined : { url: selected.qrUrl ?? '' },
    menu: shown === 'review' ? undefined : menu,
  };
  const copy = (kind: 'review' | 'menu', value: string) => void navigator.clipboard.writeText(value).then(() => setCopied(kind));
  // Fayl adında filial da var: hər filialın Google QR-ı fərqlidir; menyu QR-ı şirkət üzrə birdir
  const svg = (value: string) => new Blob([qrSvg(value)], { type: 'image/svg+xml' });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Printer className="size-5 text-primary" aria-hidden="true" />
          {t('platform.reviewCard.title')}
        </CardTitle>
        <CardDescription>{t('platform.reviewCard.description')}</CardDescription>
      </CardHeader>
      <CardContent className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_auto]">
        <div className="flex min-w-0 flex-col gap-5">
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            {t('platform.reviewCard.branch')}
            <Select
              value={selected.branchId}
              onChange={(event) => {
                setBranchId(event.target.value);
                setCopied(undefined);
              }}
            >
              {cards.map((item) => (
                <option key={item.branchId} value={item.branchId}>
                  {resolveText(item.name, lang)}
                </option>
              ))}
            </Select>
            {!card.review ? null : selected.googleReviewUrl ? (
              <span className="flex items-center gap-1.5 text-sm font-normal text-[var(--success-ink)]">
                <Check className="size-4" aria-hidden="true" />
                {t('platform.reviewCard.linkSet')}
              </span>
            ) : (
              <span className="flex items-start gap-1.5 text-sm font-normal text-muted-foreground">
                <TriangleAlert className="mt-0.5 size-4 shrink-0 text-amber-500" aria-hidden="true" />
                {t('platform.reviewCard.linkMissing')}
              </span>
            )}
          </label>

          <Choice
            label={t('platform.reviewCard.content')}
            value={shown}
            options={CONTENTS.map((value) => ({
              value,
              title: t(`platform.reviewCard.contents.${value}`),
              hint: t(`platform.reviewCard.contentHints.${value}`),
              disabled: value !== 'review' && !menuOpen,
            }))}
            onChange={setContent}
          />
          {menuOpen ? null : (
            <p className="-mt-3 flex items-start gap-1.5 text-sm text-muted-foreground">
              <BookOpen className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t('platform.reviewCard.menuClosed')}
            </p>
          )}

          <Choice
            label={t('platform.reviewCard.size')}
            value={size}
            options={SIZES.map((value) => ({
              value,
              title: t(`platform.reviewCard.sizes.${value}`),
              hint: t(`platform.reviewCard.sizeHints.${value}`),
            }))}
            onChange={setSize}
          />
          <Choice
            label={t('platform.reviewCard.language')}
            value={languages}
            options={LANGUAGES.map((value) => ({ value, title: t(`platform.reviewCard.languages.${value}`) }))}
            onChange={setLanguages}
          />

          {/* Skanlar yalnız Google QR-ı üçün sayılır (menyu QR-ı birbaşa menyuya aparır) */}
          {card.review ? (
            <p className="rounded-xl bg-muted px-3.5 py-2.5 text-sm text-muted-foreground">
              {t('platform.reviewCard.scans', {
                recent: formatNumber(selected.recentScans ?? 0, lang),
                total: formatNumber(selected.totalScans ?? 0, lang),
              })}
              <span className="mt-1 block text-xs">{t('platform.reviewCard.note')}</span>
            </p>
          ) : null}

          <div className="flex flex-col gap-2">
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setPrinting(true)} disabled={printing}>
                <Printer aria-hidden="true" />
                {t('platform.reviewCard.print')}
              </Button>
              {/* QR faylları və linkləri: kartdakı QR-lar üzrə bir siyahı */}
              <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline">
                    {copied ? <Check aria-hidden="true" /> : <Download aria-hidden="true" />}
                    {copied ? t('platform.reviewCard.copied') : t('platform.reviewCard.qrActions')}
                    <ChevronDown aria-hidden="true" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  {card.review ? (
                    <>
                      <DropdownMenuItem
                        onSelect={() => downloadBlob(svg(card.review?.url ?? ''), `google-qr-${slug}-${fileSlug(branchName)}.svg`)}
                      >
                        <Star aria-hidden="true" />
                        {t('platform.reviewCard.qrReview')}
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => copy('review', card.review?.url ?? '')}>
                        <Copy aria-hidden="true" />
                        {t('platform.reviewCard.qrReviewLink')}
                      </DropdownMenuItem>
                    </>
                  ) : null}
                  {card.menu ? (
                    <>
                      <DropdownMenuItem onSelect={() => downloadBlob(svg(card.menu?.url ?? ''), `menu-qr-${slug}.svg`)}>
                        <BookOpen aria-hidden="true" />
                        {t('platform.reviewCard.qrMenu')}
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => copy('menu', card.menu?.url ?? '')}>
                        <Copy aria-hidden="true" />
                        {t('platform.reviewCard.qrMenuLink')}
                      </DropdownMenuItem>
                    </>
                  ) : null}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
            <p className="text-xs text-muted-foreground">{t('platform.reviewCard.printHint')}</p>
          </div>
        </div>

        {/*
         * Önizləmə: geniş ekranda A6-nın real ölçüsü (105 × 148 mm), telefonda ekranın eninə sığır. Kart em ilə
         * qurulub, şrift konteynerin eninə nisbətdədir (cqw) — kiçildikdə nisbətlər eyni qalır.
         */}
        <figure className="mx-auto flex w-full max-w-[105mm] min-w-0 flex-col items-center gap-2 lg:mx-0 lg:w-[105mm]">
          <div className="aspect-[105/148] w-full overflow-hidden rounded-2xl shadow-[0_20px_50px_-20px_rgb(40_20_90/0.35)] ring-1 ring-border [container-type:inline-size]">
            <ReviewCardFace {...card} fontSize={PREVIEW_FONT} />
          </div>
          <figcaption className="text-xs text-muted-foreground">{t('platform.reviewCard.preview')}</figcaption>
        </figure>
      </CardContent>
      {printing ? <PrintPages size={size} card={card} /> : null}
    </Card>
  );
}

/** Seçim düymələri (ölçü, dil): seçilən çərçivə ilə vurğulanır. */
function Choice<T extends string>({ label, value, options, onChange }: {
  label: string;
  value: T;
  options: { value: T; title: string; hint?: string; disabled?: boolean }[];
  onChange: (value: T) => void;
}) {
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-medium">{label}</legend>
      <div className="grid gap-2 sm:grid-cols-3">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={option.value === value}
            disabled={option.disabled}
            onClick={() => onChange(option.value)}
            className={cn(
              'rounded-xl border px-3.5 py-2.5 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              option.value === value ? 'border-2 border-primary bg-primary/5' : 'border-border hover:bg-muted',
              'disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent',
            )}
          >
            <span className="block font-semibold">{option.title}</span>
            {option.hint ? <span className="block text-muted-foreground">{option.hint}</span> : null}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
