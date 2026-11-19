import { useQuery } from '@tanstack/react-query';
import { Check, Copy, Download, Printer, Star, TriangleAlert } from 'lucide-react';
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
import { Select } from '@/shared/ui/input';
import { QrCode } from '@/shared/ui/QrCode';

import { platformApi, type ReviewCard } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { downloadBlob } from '../../lib/download';
import { TenantLogo } from './TenantLogo';

type Size = 'A6' | 'A5' | 'A4';
type Languages = 'az' | 'az-ru' | 'az-en';
const SIZES: readonly Size[] = ['A6', 'A5', 'A4'];
const LANGUAGES: readonly Languages[] = ['az', 'az-ru', 'az-en'];

/** Kartın əsas şrift ölçüsü (bütün ölçülər em ilə) — A6-da ~14 px; A5 √2 dəfə böyükdür. */
const BASE_FONT = { A6: '3.7mm', A5: '5.23mm', A4: '3.7mm' } as const;
/** Önizləmədə eyni nisbət konteynerin eninə görə: 3.7 mm / 105 mm ≈ 3.524% en. */
const PREVIEW_FONT = '3.524cqw';

interface CardProps {
  tenantId: string;
  companyName: string;
  branchName: string;
  brandColor: string;
  qrUrl: string;
  languages: Languages;
  /** Kartın əsas şrift ölçüsü; bütün ölçülər ona nisbətdədir. */
  fontSize: string;
}

/**
 * Çap olunan kartın üzü (A6 nisbəti, 105 × 148 mm). Bütün ölçülər em-dədir: eyni kart önizləmədə, A6 və A5 çapında
 * yalnız əsas şrift ölçüsü ilə böyüyür. Mətnlər seçilmiş dil(lər)dədir, panelin dilindən asılı deyil.
 */
function ReviewCardFace({ tenantId, companyName, branchName, brandColor, qrUrl, languages, fontSize }: CardProps) {
  const { t } = useTranslation('admin');
  const second = languages === 'az' ? undefined : languages.slice(3);
  // Kartın mətnləri seçilmiş dildə (panelin dilindən asılı deyil)
  const texts = (lng: string) => ({
    title: t('platform.reviewCard.card.title', { lng }),
    subtitle: t('platform.reviewCard.card.subtitle', { lng }),
    scan: t('platform.reviewCard.card.scan', { lng }),
    thanks: t('platform.reviewCard.card.thanks', { lng }),
  });
  const az = texts('az');
  const other = second ? texts(second) : undefined;
  return (
    <div
      className="flex size-full flex-col items-center overflow-hidden bg-white text-center text-[#151a2d] [-webkit-print-color-adjust:exact] [print-color-adjust:exact]"
      style={{ fontSize, '--brand': brandColor } as CSSProperties}
    >
      <div className="flex w-full flex-col items-center gap-[0.35em] bg-[linear-gradient(150deg,var(--brand),color-mix(in_srgb,var(--brand)_68%,#000))] px-[1.4em] pt-[1.4em] pb-[2.1em] text-white">
        <TenantLogo
          tenantId={tenantId}
          name={companyName}
          lang="az"
          className="size-[2.6em] rounded-[0.7em] bg-white text-[1.4em] text-[var(--brand)] shadow-[0_0_0_0.2em_rgb(255_255_255/0.25)]"
        />
        <p className="mt-[0.2em] text-[1.55em] leading-tight font-extrabold tracking-tight">{companyName}</p>
        <p className="text-[0.9em] opacity-85">{branchName}</p>
      </div>
      <div className="-mt-[1.35em] flex gap-[0.15em] rounded-full bg-white px-[0.9em] py-[0.5em] shadow-[0_0.4em_1em_-0.4em_rgb(0_0_0/0.3)]">
        {[0, 1, 2, 3, 4].map((index) => (
          <Star key={index} className="size-[1.9em] fill-amber-400 text-amber-400" aria-hidden="true" />
        ))}
      </div>
      <p className="mt-[0.85em] px-[0.8em] text-[1.7em] leading-[1.12] font-extrabold tracking-tight text-balance">
        {az.title}
      </p>
      {other ? (
        <p className="mt-[0.2em] px-[1em] text-[1.15em] leading-tight font-bold text-[#5b6178]">{other?.title}</p>
      ) : null}
      <p className="mt-[0.5em] px-[2em] text-[0.92em] leading-snug text-[#5b6178]">{az.subtitle}</p>
      <div className="mt-[0.9em] size-[11.5em] rounded-[1.1em] border-[0.14em] border-[color-mix(in_srgb,var(--brand)_18%,#fff)] p-[0.5em]">
        <QrCode value={qrUrl} label={az.scan} className="size-full" />
      </div>
      <p className="mt-[0.75em] px-[1.5em] text-[0.92em] leading-snug font-semibold text-[#3b3f55]">{az.scan}</p>
      {other ? <p className="px-[1.5em] text-[0.8em] leading-snug text-[#5b6178]">{other?.scan}</p> : null}
      <p className="mt-auto pb-[1em] text-[0.78em] text-[#9aa0b4]">
        {az.thanks}
        {other ? ` · ${other.thanks}` : null}
      </p>
    </div>
  );
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

/** Platforma panelində şirkətin "Çap materialları" bölməsi: Google rəyi üçün QR kart (stend) hazırlanır və çap olunur. */
export function ReviewCardTab({ tenantId, companyName, slug }: { tenantId: string; companyName: string; slug: string }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const data = useQuery({ queryKey: ['platform-review-cards', tenantId], queryFn: () => platformApi.reviewCards(tenantId) });
  const cards = data.data?.cards ?? [];
  const [branchId, setBranchId] = useState('');
  const [size, setSize] = useState<Size>('A6');
  const [languages, setLanguages] = useState<Languages>('az');
  const [printing, setPrinting] = useState(false);
  const [copied, setCopied] = useState(false);
  const selected: ReviewCard | undefined = cards.find((card) => card.branchId === branchId) ?? cards[0];

  // Çap: kartlar səhifəyə əlavə olunur, brauzerin çap pəncərəsi açılır, bağlananda təmizlənir
  useEffect(() => {
    if (!printing) {
      return;
    }
    document.body.classList.add('printing-review');
    const done = () => setPrinting(false);
    window.addEventListener('afterprint', done);
    // Loqo və şriftlər hazır olsun deyə bir kadr gözlənilir
    const frame = window.requestAnimationFrame(() => window.setTimeout(() => window.print(), 150));
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('afterprint', done);
      document.body.classList.remove('printing-review');
    };
  }, [printing]);

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
  const card = {
    tenantId,
    companyName,
    branchName,
    brandColor: data.data?.brandColor ?? '#6D28D9',
    qrUrl: selected.qrUrl ?? '',
    languages,
  };
  const copyLink = () => void navigator.clipboard.writeText(card.qrUrl).then(() => setCopied(true));

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
                setCopied(false);
              }}
            >
              {cards.map((item) => (
                <option key={item.branchId} value={item.branchId}>
                  {resolveText(item.name, lang)}
                </option>
              ))}
            </Select>
            {selected.googleReviewUrl ? (
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

          <p className="rounded-xl bg-muted px-3.5 py-2.5 text-sm text-muted-foreground">
            {t('platform.reviewCard.scans', {
              recent: formatNumber(selected.recentScans ?? 0, lang),
              total: formatNumber(selected.totalScans ?? 0, lang),
            })}
            <span className="mt-1 block text-xs">{t('platform.reviewCard.note')}</span>
          </p>

          <div className="flex flex-col gap-2">
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setPrinting(true)} disabled={printing}>
                <Printer aria-hidden="true" />
                {t('platform.reviewCard.print')}
              </Button>
              <Button
                variant="outline"
                onClick={() => downloadBlob(new Blob([qrSvg(card.qrUrl)], { type: 'image/svg+xml' }), `google-qr-${slug}.svg`)}
              >
                <Download aria-hidden="true" />
                {t('platform.reviewCard.downloadQr')}
              </Button>
              <Button variant="ghost" onClick={copyLink}>
                {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                {copied ? t('platform.reviewCard.copied') : t('platform.reviewCard.copyLink')}
              </Button>
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
  options: { value: T; title: string; hint?: string }[];
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
            onClick={() => onChange(option.value)}
            className={cn(
              'rounded-xl border px-3.5 py-2.5 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              option.value === value ? 'border-2 border-primary bg-primary/5' : 'border-border hover:bg-muted',
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
