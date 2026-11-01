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

const AZ_UNITS: Readonly<Record<Intl.RelativeTimeFormatUnit, string>> = {
  year: 'il',
  years: 'il',
  quarter: 'rüb',
  quarters: 'rüb',
  month: 'ay',
  months: 'ay',
  week: 'həftə',
  weeks: 'həftə',
  day: 'gün',
  days: 'gün',
  hour: 'saat',
  hours: 'saat',
  minute: 'dəqiqə',
  minutes: 'dəqiqə',
  second: 'saniyə',
  seconds: 'saniyə',
};

/** Azərbaycan dili üçün nisbi vaxt əl ilə (Edge/Chromium ICU-da az məlumatı yoxdur: "-1 h" göstərir). */
function azRelative(amount: number, unit: Intl.RelativeTimeFormatUnit): string {
  if (unit === 'second' && Math.abs(amount) < 45) {
    return 'indicə';
  }
  if (unit === 'day' && amount === -1) {
    return 'dünən';
  }
  if (unit === 'day' && amount === 1) {
    return 'sabah';
  }
  return amount < 0 ? `${-amount} ${AZ_UNITS[unit]} əvvəl` : `${amount} ${AZ_UNITS[unit]} sonra`;
}

/** Nisbi vaxt: "3 gün əvvəl" / "3 days ago" / "3 дня назад". */
export function formatRelative(value: DateInput, lang: Language, now: DateInput = Date.now()): string {
  const diffSeconds = Math.round((toDate(value).getTime() - toDate(now).getTime()) / 1000);
  if (lang === 'az') {
    for (const [unit, seconds] of RELATIVE_UNITS) {
      if (Math.abs(diffSeconds) >= seconds || unit === 'second') {
        return azRelative(Math.round(diffSeconds / seconds), unit);
      }
    }
  }
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

/**
 * Pul məbləği. Azərbaycan dilində manat əl ilə yazılır ("1 234,50 ₼"): bəzi brauzerlərin (məs. Chromium) dil
 * məlumatında {@code az} üçün valyuta formatı yoxdur və "AZN 1234.50" göstərilir.
 */
export function formatCurrency(value: number, lang: Language, currency = 'AZN'): string {
  if (lang === 'az' && currency === 'AZN') {
    const digits = new Intl.NumberFormat('ru', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    return `${digits} ₼`;
  }
  return new Intl.NumberFormat(lang, { style: 'currency', currency }).format(value);
}

export function formatPercent(value: number, lang: Language, maximumFractionDigits = 1): string {
  return new Intl.NumberFormat(lang, { style: 'percent', maximumFractionDigits }).format(value);
}

/** Telefon nömrəsi (E.164) oxunaqlı qruplarla: +994501234567 → "+994 50 123 45 67"; digər ölkələr olduğu kimi. */
export function formatPhone(e164: string | undefined): string {
  const az = /^\+994(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(e164 ?? '');
  return az ? `+994 ${az[1]} ${az[2]} ${az[3]} ${az[4]}` : (e164 ?? '');
}
