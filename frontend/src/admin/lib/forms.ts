import { useTranslation } from 'react-i18next';
import { z } from 'zod';

import type commonAz from '@/locales/az/common.json';
import type authAz from '@/locales/az/auth.json';

/** Zod mesajları tərcümə açarlarıdır (`common:validation.*`) — mətn render zamanı cari dildə götürülür. */
export type ValidationKey = keyof (typeof commonAz)['validation'];
export type PasswordViolation = Exclude<keyof (typeof authAz)['passwordPolicy'], 'hint'>;

export function useValidationMessage(): (message: string | undefined) => string | undefined {
  const { t } = useTranslation('common');
  return (message) => (message ? t(`validation.${message as ValidationKey}`) : undefined);
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const emailField = z.string().trim().min(1, 'required').max(254, 'tooLong').regex(EMAIL, 'email');
export const requiredPassword = z.string().min(1, 'required').max(128, 'tooLong');
export const totpCode = z.string().trim().regex(/^\d{6}$/, 'code');
export const recoveryCode = z
  .string()
  .trim()
  .regex(/^[A-Za-z0-9]{5}-?[A-Za-z0-9]{5}$/, 'recoveryCode');

/** Server tərəfindəki siyasətin əsas hissəsi (uzunluq) dərhal göstərilir; tam yoxlama backend-dədir. */
export const newPassword = z.string().min(1, 'required').max(128, 'tooLong');

export const MIN_PASSWORD_LENGTH = 12;
