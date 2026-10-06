import azAuth from '@/locales/az/auth.json';
import azCommon from '@/locales/az/common.json';
import azErrors from '@/locales/az/errors.json';
import azProfile from '@/locales/az/profile.json';
import enAuth from '@/locales/en/auth.json';
import enCommon from '@/locales/en/common.json';
import enErrors from '@/locales/en/errors.json';
import enProfile from '@/locales/en/profile.json';
import ruAuth from '@/locales/ru/auth.json';
import ruCommon from '@/locales/ru/common.json';
import ruErrors from '@/locales/ru/errors.json';
import ruProfile from '@/locales/ru/profile.json';
import { setupI18n } from '@/shared/i18n/setup';

/** Müştəri tətbiqi yalnız öz namespace-lərini yükləyir (admin mətnləri telefona göndərilmir). */
export const customerI18n = setupI18n(
  {
    az: { common: azCommon, auth: azAuth, profile: azProfile, errors: azErrors },
    en: { common: enCommon, auth: enAuth, profile: enProfile, errors: enErrors },
    ru: { common: ruCommon, auth: ruAuth, profile: ruProfile, errors: ruErrors },
  },
  ['common', 'auth', 'profile', 'errors'],
);
