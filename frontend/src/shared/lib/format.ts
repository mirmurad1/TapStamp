import type { Language } from '@/shared/i18n/languages';

/** Bütün tarix və saatlar standart olaraq şirkətin saat qurşağında göstərilir (bazada UTC saxlanılır). */
export const DEFAULT_TIME_ZONE = 'Asia/Baku';

type DateInput = Date | string | number;

function toDate(value: DateInput): Date {
  return value instanceof Date ? value : new Date(value);
}

/**
 * Bəzi brauzerlərin (Chromium, Edge) ICU məlumatında Azərbaycan dili üçün tarix formatı natamamdır və "2026 M10 7"
 * kimi nəticə verir. Ona görə az dilində tarix əl ilə qurulur: "7 oktyabr 2026".
 */
const AZ_MONTHS = [
  'yanvar',
  'fevral',
  'mart',
  'aprel',
  'may',
  'iyun',
  'iyul',
  'avqust',
  'sentyabr',
  'oktyabr',
  'noyabr',
  'dekabr',
] as const;

function azDate(date: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-US', { day: 'numeric', month: 'numeric', year: 'numeric', timeZone })
    .formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  return `${part('day')} ${AZ_MONTHS[part('month') - 1] ?? ''} ${part('year')}`;
}

export function formatDate(value: DateInput, lang: Language, timeZone = DEFAULT_TIME_ZONE): string {
  if (lang === 'az') {
    return azDate(toDate(value), timeZone);
  }
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium', timeZone }).format(toDate(value));
}

export function formatDateTime(value: DateInput, lang: Language, timeZone = DEFAULT_TIME_ZONE): string {
  if (lang === 'az') {
    return `${azDate(toDate(value), timeZone)}, ${formatTime(value, lang, timeZone)}`;
  }
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(toDate(value));
}

export function formatTime(value: DateInput, lang: Language, timeZone = DEFAULT_TIME_ZONE): string {
  return new Intl.DateTimeFormat(lang, { timeStyle: 'short', timeZone }).format(toDate(value));
}

const RELATIVE_UNITS: ReadonlyArray<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 365 * 24 * 60 * 60],
  ['month', 30 * 24 * 60 * 60],
  ['week', 7 * 24 * 60 * 60],
  ['day', 24 * 60 * 60],
  ['hour', 60 * 60],
  ['minute', 60],
  ['second', 1],
];

/** Nisbi vaxt: "3 gün əvvəl" / "3 days ago" / "3 дня назад". */
export function formatRelative(value: DateInput, lang: Language, now: DateInput = Date.now()): string {
  const diffSeconds = Math.round((toDate(value).getTime() - toDate(now).getTime()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(lang, { numeric: 'auto' });
  for (const [unit, seconds] of RELATIVE_UNITS) {
    if (Math.abs(diffSeconds) >= seconds || unit === 'second') {
      return formatter.format(Math.round(diffSeconds / seconds), unit);
    }
  }
  return formatter.format(0, 'second');
}

export function formatNumber(value: number, lang: Language): string {
  return new Intl.NumberFormat(lang).format(value);
}

export function formatCurrency(value: number, lang: Language, currency = 'AZN'): string {
  return new Intl.NumberFormat(lang, { style: 'currency', currency }).format(value);
}

export function formatPercent(value: number, lang: Language, maximumFractionDigits = 1): string {
  return new Intl.NumberFormat(lang, { style: 'percent', maximumFractionDigits }).format(value);
}
