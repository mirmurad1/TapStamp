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

/** Brend rəngini CSS dəyişənlərinə yazır; yanlış dəyər nəzərə alınmır (standart tema qalır). */
export function applyBrandColor(hex: string | undefined, root: HTMLElement = document.documentElement): void {
  if (!hex || !HEX.test(hex)) {
    return;
  }
  root.style.setProperty('--primary', hex);
  root.style.setProperty('--ring', hex);
  root.style.setProperty('--primary-foreground', readableForeground(hex));
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
