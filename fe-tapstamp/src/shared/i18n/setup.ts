import i18n, { type Resource } from 'i18next';
import { initReactI18next } from 'react-i18next';

import {
  DEFAULT_LANGUAGE,
  detectInitialLanguage,
  SUPPORTED_LANGUAGES,
  toSupportedLanguage,
  writeLangCookie,
  type Language,
} from './languages';

/** i18next-i işə salır. Resurslar hər tətbiq (müştəri / admin) üçün ayrıca verilir ki, lazımsız mətnlər yüklənməsin. */
export function setupI18n(resources: Resource, namespaces: string[]): typeof i18n {
  const lng = detectInitialLanguage();
  void i18n.use(initReactI18next).init({
    resources,
    lng,
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: [...SUPPORTED_LANGUAGES],
    ns: namespaces,
    defaultNS: 'common',
    initAsync: false,
    interpolation: { escapeValue: false },
    returnNull: false,
    react: { useSuspense: false },
  });
  document.documentElement.lang = lng;
  i18n.on('languageChanged', (changed) => {
    document.documentElement.lang = changed;
  });
  return i18n;
}

export function currentLanguage(): Language {
  return toSupportedLanguage(i18n.resolvedLanguage ?? i18n.language) ?? DEFAULT_LANGUAGE;
}

/**
 * İstifadəçinin açıq seçimi: dil dərhal, səhifə yenilənmədən dəyişir (formalardakı məlumat itmir)
 * və cookie-yə yazılır. Daxil olmuş istifadəçi üçün profilə yazmaq sonrakı mərhələdə əlavə olunur.
 */
export async function changeLanguage(lang: Language): Promise<void> {
  writeLangCookie(lang);
  await i18n.changeLanguage(lang);
}
