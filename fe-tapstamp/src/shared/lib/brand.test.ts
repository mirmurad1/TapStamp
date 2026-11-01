import { describe, expect, it } from 'vitest';

import { applyBrandColor, applyIconVersion, contrastRatio, formatCountdown, readableForeground } from './brand';

describe('ikon versiyası', () => {
  const head = () => {
    const el = document.createElement('head');
    el.innerHTML =
      '<link rel="icon" href="/api/v1/public/icons/192.png"><link rel="apple-touch-icon" href="/api/v1/public/icons/180.png">' +
      '<link rel="manifest" href="/manifest.webmanifest">';
    return el;
  };
  const hrefs = (el: HTMLElement) => [...el.querySelectorAll('link')].map((l) => l.getAttribute('href'));

  it('rəng və loqo versiyası ikon ünvanlarına yazılır, manifestə toxunulmur', () => {
    const el = head();
    applyIconVersion('#7C3AED', '/api/v1/public/company/logo?v=3', el);
    expect(hrefs(el)).toEqual([
      '/api/v1/public/icons/192.png?v=7c3aed-l3',
      '/api/v1/public/icons/180.png?v=7c3aed-l3',
      '/manifest.webmanifest',
    ]);
    applyIconVersion('#7C3AED', undefined, el);
    expect(hrefs(el)[1]).toBe('/api/v1/public/icons/180.png?v=7c3aed');
  });
});

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
    // Tünd rəng olduğu kimi qalır
    applyBrandColor('#6D28D9', root);
    expect(root.style.getPropertyValue('--brand-light')).toBe('#6d28d9');
    expect(root.style.getPropertyValue('--brand-light-foreground')).toBe('#ffffff');
    // Açıq göy rəng açıq fonda oxunmur — tündləşdirilir
    applyBrandColor('#0EA5E9', root);
    expect(contrastRatio(root.style.getPropertyValue('--brand-light'), '#ffffff')).toBeGreaterThanOrEqual(4.5);

  });

  it('geri sayımı formatlayır', () => {
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(75)).toBe('1:15');
    expect(formatCountdown(840.2)).toBe('14:01');
    expect(formatCountdown(3725)).toBe('1:02:05');
  });
});

describe('brend rənginin yadda saxlanması', () => {
  it('son rəng növbəti açılışda React-dan əvvəl tətbiq olunur', async () => {
    const { applyCachedBrandColor } = await import('./brand');
    window.localStorage.clear();
    const first = document.createElement('div');
    applyCachedBrandColor(first);
    expect(first.style.getPropertyValue('--brand-light')).toBe('');

    applyBrandColor('#806840', document.createElement('div'));
    const next = document.createElement('div');
    applyCachedBrandColor(next);
    expect(next.style.getPropertyValue('--brand-light')).toBe('#806840');

    // Yanlış dəyər nəzərə alınmır
    window.localStorage.setItem('tapstamp.brandColor', 'red;}');
    const broken = document.createElement('div');
    applyCachedBrandColor(broken);
    expect(broken.style.getPropertyValue('--brand-light')).toBe('');
  });
});
