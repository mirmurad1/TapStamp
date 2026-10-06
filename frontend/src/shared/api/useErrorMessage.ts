import { useTranslation } from 'react-i18next';

import errorsAz from '@/locales/az/errors.json';

import { ApiError } from './client';

type ErrorCode = keyof typeof errorsAz;

function isKnownCode(code: string): code is ErrorCode {
  return Object.prototype.hasOwnProperty.call(errorsAz, code);
}

/** Xətanı istifadəçinin dilində mətnə çevirir (`errors:<CODE>`); naməlum kod üçün ümumi mesaj. */
export function useErrorMessage(): (error: unknown) => string {
  const { t } = useTranslation('errors');
  return (error: unknown) => {
    const code = error instanceof ApiError ? error.code : 'UNKNOWN';
    return t(isKnownCode(code) ? code : 'UNKNOWN');
  };
}
