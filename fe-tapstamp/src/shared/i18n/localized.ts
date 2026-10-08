import type { Language } from './languages';

/** Admin tərəfindən 3 dildə daxil edilən mətn (backend-in LocalizedText modeli). */
export interface LocalizedText {
  az?: string | null;
  en?: string | null;
  ru?: string | null;
}

/** Cari dildəki mətn; tərcümə yoxdursa Azərbaycan dilindəki versiya (backend-lə eyni qayda). */
export function resolveText(text: LocalizedText | null | undefined, lang: Language): string {
  if (!text) {
    return '';
  }
  const value = text[lang];
  return value && value.trim() ? value : (text.az ?? '');
}
