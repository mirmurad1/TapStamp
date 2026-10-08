export const SUPPORTED_LANGUAGES = ['az', 'en', 'ru'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'az';
export const LANG_COOKIE = 'lang';

/** Dil seçicisində göstərilən qısa kodlar (bütün dillərdə eynidir). */
export const LANGUAGE_CODES: Record<Language, string> = { az: 'AZ', en: 'EN', ru: 'RU' };

/** "az-Latn-AZ", "ru_RU", "EN" kimi dəyərləri dəstəklənən dilə çevirir. */
export function toSupportedLanguage(value: string | null | undefined): Language | undefined {
  if (!value) {
    return undefined;
  }
  const primary = value.trim().replace('_', '-').split('-')[0]?.toLowerCase();
  return SUPPORTED_LANGUAGES.find((lang) => lang === primary);
}

export function readLangCookie(cookieString: string = document.cookie): Language | undefined {
  for (const part of cookieString.split(';')) {
    const [name, ...rest] = part.trim().split('=');
    if (name === LANG_COOKIE) {
      return toSupportedLanguage(decodeURIComponent(rest.join('=')));
    }
  }
  return undefined;
}

/** Yalnız istifadəçi dili açıq şəkildə seçəndə yazılır; backend də bu cookie-ni oxuyur. */
export function writeLangCookie(lang: Language): void {
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${LANG_COOKIE}=${lang}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`;
}

export function fromNavigator(languages: readonly string[] = navigator.languages): Language | undefined {
  for (const candidate of languages) {
    const lang = toSupportedLanguage(candidate);
    if (lang) {
      return lang;
    }
  }
  return undefined;
}

/**
 * Daxil olmamış istifadəçi üçün dilin prioritet sırası: lang cookie → brauzer dilləri → az.
 * Daxil olmuş istifadəçinin profilindəki dil isə /me cavabı gələndə tətbiq olunur.
 */
export function detectInitialLanguage(): Language {
  return readLangCookie() ?? fromNavigator() ?? DEFAULT_LANGUAGE;
}
