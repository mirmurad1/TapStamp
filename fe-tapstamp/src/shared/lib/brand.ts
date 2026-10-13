/**
 * Şirkətin brend rəngi müştəri tətbiqinin əsas rəngi olur (düymələr, möhürlər). Mətn rəngi kontrasta görə seçilir:
 * açıq brend rəngində qara, tünd rəngdə ağ (WCAG nisbi parlaqlıq).
 */
const HEX = /^#([0-9a-f]{6})$/i;

export function readableForeground(hex: string): '#ffffff' | '#111827' {
  const match = HEX.exec(hex);
  if (!match?.[1]) {
    return '#ffffff';
  }
  const value = parseInt(match[1], 16);
  const [r, g, b] = [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff].map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  const luminance = 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0);
  // Ağ mətnlə kontrast (1.05 / (L + 0.05)) qara ilə kontrastdan böyükdürsə ağ seçilir
  return 1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05 ? '#ffffff' : '#111827';
}

function luminance(hex: string): number {
  const value = parseInt(hex.slice(1), 16);
  const [r, g, b] = [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff].map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0);
}

/** WCAG kontrast nisbəti (1–21). */
export function contrastRatio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return ((hi ?? 0) + 0.05) / ((lo ?? 0) + 0.05);
}

function mix(hex: string, target: string, amount: number): string {
  const from = parseInt(hex.slice(1), 16);
  const to = parseInt(target.slice(1), 16);
  const channel = (shift: number) =>
    Math.round(((from >> shift) & 0xff) + ((((to >> shift) & 0xff) - ((from >> shift) & 0xff)) * amount));
  return `#${[16, 8, 0].map((shift) => channel(shift).toString(16).padStart(2, '0')).join('')}`;
}

/** Rəngi səthlər üzərində oxunaqlı edir (kontrast ≥ 4.5): lazım olduqca tündləşdirir. */
function readableOn(hex: string, surfaces: string[], towards: string): string {
  for (let amount = 0; amount <= 1; amount += 0.05) {
    const candidate = amount === 0 ? hex.toLowerCase() : mix(hex, towards, amount);
    if (surfaces.every((surface) => contrastRatio(candidate, surface) >= 4.5)) {
      return candidate;
    }
  }
  return towards;
}

const LIGHT_SURFACES = ['#ffffff', '#f6f7fb'];

/**
 * Brend rəngini CSS dəyişənlərinə yazır; yanlış dəyər nəzərə alınmır (standart tema qalır). Şirkətin rəngi ağ
 * fona qarşı zəif görünürsə (məs. açıq sarı), rəng tonunu saxlayaraq tündləşdirilir ki, mətn və düymələr oxunaqlı
 * olsun (WCAG AA).
 */
export function applyBrandColor(hex: string | undefined, root: HTMLElement = document.documentElement): void {
  if (!hex || !HEX.test(hex)) {
    return;
  }
  const light = readableOn(hex, LIGHT_SURFACES, '#000000');
  root.style.setProperty('--brand-light', light);
  root.style.setProperty('--brand-light-foreground', readableForeground(light));
}

/**
 * index.html-dəki ikon ünvanlarına (iPhone-un ana ekran ikonu və brauzer tabı) versiya əlavə edir: rəng və ya loqo
 * dəyişəndə ünvan dəyişir, telefon köhnə ikonu keşdən götürmür. Versiya serverin manifestdəki versiyası ilə eyni
 * formadadır ("<rəng>" və ya "<rəng>-l<loqo versiyası>").
 */
export function applyIconVersion(
  brandColor: string | undefined,
  logoUrl: string | undefined,
  head: HTMLElement = document.head,
): void {
  if (!brandColor || !HEX.test(brandColor)) {
    return;
  }
  const logoVersion = logoUrl ? /[?&]v=([^&]+)/.exec(logoUrl)?.[1] : undefined;
  const version = brandColor.slice(1).toLowerCase() + (logoVersion ? `-l${logoVersion}` : '');
  head.querySelectorAll<HTMLLinkElement>('link[rel="apple-touch-icon"], link[rel="icon"]').forEach((link) => {
    const path = link.getAttribute('href')?.split('?')[0];
    if (path?.startsWith('/api/v1/public/icons/')) {
      link.setAttribute('href', `${path}?v=${version}`);
    }
  });
}

/** Saniyəni geri sayım formatına çevirir: 75 → "1:15", 3725 → "1:02:05". */
export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.ceil(totalSeconds));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}
