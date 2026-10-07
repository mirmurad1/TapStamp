import { describe, expect, it } from 'vitest';

import { formatCurrency, formatDate, formatDateTime, formatRelative, formatTime } from './format';

// Intl çıxışında adi boşluq əvəzinə NBSP və ya dar NBSP ola bilər — müqayisə üçün normallaşdırırıq
const normalize = (value: string) => value.replace(/[\u00a0\u202f]/g, ' ');

describe('lokallaşdırılmış formatlar', () => {
  const utcMorning = '2026-10-06T05:30:00Z'; // Bakı vaxtı ilə 09:30

  it('vaxt Asia/Baku saat qurşağında göstərilir', () => {
    expect(formatTime(utcMorning, 'az')).toBe('09:30');
    expect(formatTime(utcMorning, 'ru')).toBe('09:30');
    expect(normalize(formatTime(utcMorning, 'en'))).toBe('9:30 AM');
  });

  it('tarix dilə uyğun formatlanır', () => {
    expect(normalize(formatDateTime(utcMorning, 'ru'))).toContain('2026');
    expect(normalize(formatDateTime(utcMorning, 'az'))).toContain('2026');
    // Brauzerin ICU məlumatından asılı olmayaraq ayın adı ilə ("2026 M10 7" yox)
    expect(formatDate('2026-10-06T21:30:00Z', 'az')).toBe('7 oktyabr 2026');
    expect(formatDate('2026-01-31T12:00:00Z', 'az')).toBe('31 yanvar 2026');
  });

  it('nisbi vaxt 3 dildə', () => {
    const now = '2026-10-06T12:00:00Z';
    const threeDaysAgo = '2026-10-03T12:00:00Z';
    expect(formatRelative(threeDaysAgo, 'az', now)).toBe('3 gün öncə');
    expect(formatRelative(threeDaysAgo, 'en', now)).toBe('3 days ago');
    expect(formatRelative(threeDaysAgo, 'ru', now)).toBe('3 дня назад');
  });

  it('valyuta AZN ilə', () => {
    expect(normalize(formatCurrency(4.5, 'az'))).toMatch(/4,50.*₼|₼.*4,50/);
    expect(normalize(formatCurrency(4.5, 'en'))).toContain('4.50');
  });
});
