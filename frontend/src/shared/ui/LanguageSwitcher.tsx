import { useTranslation } from 'react-i18next';

import { LANGUAGE_CODES, SUPPORTED_LANGUAGES, type Language } from '@/shared/i18n/languages';
import { changeLanguage, currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';

interface LanguageSwitcherProps {
  className?: string;
}

/**
 * Kompakt dil seçicisi (AZ / EN / RU). Dil dərhal dəyişir: səhifə yenilənmir və komponentlər
 * yenidən qurulmur, ona görə yarımçıq doldurulmuş formalar (məs. OTP) itmir.
 */
export function LanguageSwitcher({ className }: LanguageSwitcherProps) {
  const { t, i18n } = useTranslation('common');
  const active = currentLanguage();

  const select = (lang: Language) => {
    if (lang !== i18n.language) {
      void changeLanguage(lang);
    }
  };

  return (
    <div
      role="radiogroup"
      aria-label={t('language.label')}
      className={cn('inline-flex rounded-full border border-border bg-card p-0.5 shadow-sm', className)}
    >
      {SUPPORTED_LANGUAGES.map((lang) => (
        <button
          key={lang}
          type="button"
          role="radio"
          aria-checked={active === lang}
          lang={lang}
          title={t(`language.${lang}`)}
          onClick={() => select(lang)}
          className={cn(
            'min-h-9 min-w-11 rounded-full px-2.5 text-xs font-semibold tracking-wide transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            active === lang ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {LANGUAGE_CODES[lang]}
        </button>
      ))}
    </div>
  );
}
