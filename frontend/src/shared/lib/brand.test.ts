import { describe, expect, it } from 'vitest';

import { applyBrandColor, formatCountdown, readableForeground } from './brand';

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
    expect(root.style.getPropertyValue('--primary')).toBe('');
    applyBrandColor('#0EA5E9', root);
    expect(root.style.getPropertyValue('--primary')).toBe('#0EA5E9');
    expect(root.style.getPropertyValue('--primary-foreground')).toBe('#111827');
  });

  it('geri sayımı formatlayır', () => {
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(75)).toBe('1:15');
    expect(formatCountdown(840.2)).toBe('14:01');
    expect(formatCountdown(3725)).toBe('1:02:05');
  });
});
