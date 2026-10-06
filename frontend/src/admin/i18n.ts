import azAdmin from '@/locales/az/admin.json';
import azAuth from '@/locales/az/auth.json';
import azCommon from '@/locales/az/common.json';
import azErrors from '@/locales/az/errors.json';
import enAdmin from '@/locales/en/admin.json';
import enAuth from '@/locales/en/auth.json';
import enCommon from '@/locales/en/common.json';
import enErrors from '@/locales/en/errors.json';
import ruAdmin from '@/locales/ru/admin.json';
import ruAuth from '@/locales/ru/auth.json';
import ruCommon from '@/locales/ru/common.json';
import ruErrors from '@/locales/ru/errors.json';
import { setupI18n } from '@/shared/i18n/setup';

export const adminI18n = setupI18n(
  {
    az: { common: azCommon, auth: azAuth, admin: azAdmin, errors: azErrors },
    en: { common: enCommon, auth: enAuth, admin: enAdmin, errors: enErrors },
    ru: { common: ruCommon, auth: ruAuth, admin: ruAdmin, errors: ruErrors },
  },
  ['common', 'auth', 'admin', 'errors'],
);
