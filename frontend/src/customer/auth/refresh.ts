import { CSRF_HEADER, readCookie, CSRF_COOKIE } from '@/shared/api/client';

const LOCK_NAME = 'cafe-loyalty:session-refresh';

let inflight: Promise<boolean> | undefined;

async function callRefresh(): Promise<boolean> {
  let csrf = readCookie(CSRF_COOKIE);
  if (!csrf) {
    await globalThis.fetch('/api/v1/csrf', { credentials: 'same-origin' });
    csrf = readCookie(CSRF_COOKIE);
  }
  try {
    const response = await globalThis.fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { [CSRF_HEADER]: csrf ?? '' },
    });
    // 409 REFRESH_RACE: başqa tab artıq yeniləyib — yeni cookie-lər brauzerdədir
    return response.ok || response.status === 409;
  } catch {
    return false;
  }
}

/**
 * Sessiyanın yenilənməsi (refresh token rotasiyası). Eyni anda yalnız bir sorğu gedir: bu tabda — ümumi Promise ilə,
 * tablar arasında — {@code navigator.locks} ilə (dəstəklənirsə). Kilidi gözləyən tab yenilənmiş cookie ilə davam edir.
 */
export function refreshSession(): Promise<boolean> {
  inflight ??= (
    typeof navigator !== 'undefined' && navigator.locks
      ? navigator.locks.request(LOCK_NAME, callRefresh)
      : callRefresh()
  ).finally(() => {
    inflight = undefined;
  });
  return inflight;
}
