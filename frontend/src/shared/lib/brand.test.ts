import { describe, expect, it } from 'vitest';

import { applyBrandColor, contrastRatio, formatCountdown, readableForeground } from './brand';

describe('brend rəngi', () => {
  it('kontrasta görə mətn rəngi seçir', () => {
    expect(readableForeground('#6d28d9')).toBe('#ffffff');
    expect(readableForeground('#0f172a')).toBe('#ffffff');
    expect(readableForeground('#fde047')).toBe('#111827');
    expect(readableForeground('#ffffff')).toBe('#111827');
  });

  it('düzgün rəngi tətbiq edir, yanlışı nəzərə almır', () => {
    const root = document.createElement('div');
    applyBrandColor('url(javascript:alert(1))', root);
    expect(root.style.getPropertyValue('--brand-light')).toBe('');
    // Tünd rəng açıq rejimdə olduğu kimi qalır, qaranlıq rejim üçün açılır
    applyBrandColor('#6D28D9', root);
    expect(root.style.getPropertyValue('--brand-light')).toBe('#6d28d9');
    expect(root.style.getPropertyValue('--brand-light-foreground')).toBe('#ffffff');
    expect(contrastRatio(root.style.getPropertyValue('--brand-dark'), '#181c33')).toBeGreaterThanOrEqual(4.5);
    // Açıq göy rəng açıq fonda oxunmur — tündləşdirilir
    applyBrandColor('#0EA5E9', root);
    expect(contrastRatio(root.style.getPropertyValue('--brand-light'), '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(root.style.getPropertyValue('--brand-dark')).toBe('#0ea5e9');
  });

  it('geri sayımı formatlayır', () => {
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(75)).toBe('1:15');
    expect(formatCountdown(840.2)).toBe('14:01');
    expect(formatCountdown(3725)).toBe('1:02:05');
  });
});
