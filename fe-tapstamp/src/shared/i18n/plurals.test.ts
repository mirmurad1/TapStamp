import i18next from 'i18next';
import { beforeAll, describe, expect, it } from 'vitest';

import azCommon from '@/locales/az/common.json';
import enCommon from '@/locales/en/common.json';
import ruCommon from '@/locales/ru/common.json';

const i18n = i18next.createInstance();

beforeAll(async () => {
  await i18n.init({
    resources: { az: { common: azCommon }, en: { common: enCommon }, ru: { common: ruCommon } },
    lng: 'az',
    defaultNS: 'common',
    interpolation: { escapeValue: false },
  });
});

describe('cəm formaları', () => {
  it('rus dilində 1 / 2 / 5 / 21 штамп düzgün formalaşır', () => {
    const t = i18n.getFixedT('ru');
    expect(t('units.stamp', { count: 1 })).toBe('1 штамп');
    expect(t('units.stamp', { count: 2 })).toBe('2 штампа');
    expect(t('units.stamp', { count: 5 })).toBe('5 штампов');
    expect(t('units.stamp', { count: 11 })).toBe('11 штампов');
    expect(t('units.stamp', { count: 21 })).toBe('21 штамп');
    expect(t('units.visit', { count: 5 })).toBe('5 визитов');
    expect(t('units.visit', { count: 3 })).toBe('3 визита');
  });

  it('ingilis dilində tək və cəm', () => {
    const t = i18n.getFixedT('en');
    expect(t('units.stamp', { count: 1 })).toBe('1 stamp');
    expect(t('units.stamp', { count: 3 })).toBe('3 stamps');
  });

  it('Azərbaycan dilində sayla isim tək qalır', () => {
    const t = i18n.getFixedT('az');
    expect(t('units.stamp', { count: 1 })).toBe('1 möhür');
    expect(t('units.stamp', { count: 7 })).toBe('7 möhür');
  });
});
