import 'i18next';

import type admin from '@/locales/az/admin.json';
import type auth from '@/locales/az/auth.json';
import type common from '@/locales/az/common.json';
import type errors from '@/locales/az/errors.json';
import type profile from '@/locales/az/profile.json';

/**
 * Tərcümə açarlarının tip təhlükəsizliyi: az faylları mənbə kimi götürülür.
 * Mövcud olmayan açar (məs. t('profile:card.titlee')) TypeScript xətası verir və build dayanır.
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      common: typeof common;
      auth: typeof auth;
      profile: typeof profile;
      admin: typeof admin;
      errors: typeof errors;
    };
  }
}
