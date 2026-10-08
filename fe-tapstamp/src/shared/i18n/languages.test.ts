import { afterEach, describe, expect, it } from 'vitest';

import { fromNavigator, readLangCookie, toSupportedLanguage, writeLangCookie } from './languages';

describe('dilin müəyyənləşdirilməsi', () => {
  afterEach(() => {
    document.cookie = 'lang=; Path=/; Max-Age=0';
  });

  it('teq variantlarını tanıyır', () => {
    expect(toSupportedLanguage('az-Latn-AZ')).toBe('az');
    expect(toSupportedLanguage('ru_RU')).toBe('ru');
    expect(toSupportedLanguage('EN')).toBe('en');
    expect(toSupportedLanguage('tr')).toBeUndefined();
    expect(toSupportedLanguage('')).toBeUndefined();
  });

  it('brauzer dillərindən dəstəklənən ilkini seçir', () => {
    expect(fromNavigator(['de-DE', 'ru-RU', 'en'])).toBe('ru');
    expect(fromNavigator(['fr'])).toBeUndefined();
  });

  it('lang cookie-sini yazır və oxuyur', () => {
    writeLangCookie('ru');
    expect(readLangCookie()).toBe('ru');
    expect(readLangCookie('theme=dark; lang=en; other=1')).toBe('en');
    expect(readLangCookie('lang=<script>')).toBeUndefined();
  });
});
