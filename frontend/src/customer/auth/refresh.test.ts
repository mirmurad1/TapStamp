import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { api, setSessionRefresher, unwrap } from '@/shared/api/client';
import { installCookieJar } from '@/test/cookieJar';

import { refreshSession } from './refresh';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('sessiyanın avtomatik yenilənməsi', () => {
  let restoreCookies: () => void;

  beforeEach(() => {
    restoreCookies = installCookieJar({ '__Host-XSRF-TOKEN': 'csrf' });
    setSessionRefresher(refreshSession);
  });

  afterEach(() => {
    setSessionRefresher(undefined);
    vi.unstubAllGlobals();
    restoreCookies();
  });

  it('401-də sessiyanı bir dəfə yeniləyir və sorğunu təkrarlayır; paralel sorğular bir yeniləməni gözləyir', async () => {
    let refreshed = false;
    const fetchMock = vi.fn(async (input: Request | string) => {
      const url = typeof input === 'string' ? input : input.url;
      if (url.endsWith('/api/v1/auth/refresh')) {
        await new Promise((resolve) => setTimeout(resolve, 20));
        refreshed = true;
        return json({ status: 'AUTHENTICATED' });
      }
      return refreshed ? json({ id: 'me', name: 'Aysel' }) : json({ code: 'SESSION_EXPIRED' }, 401);
    });
    vi.stubGlobal('fetch', fetchMock);

    const [first, second] = await Promise.all([unwrap(api.GET('/api/v1/me')), unwrap(api.GET('/api/v1/me/sessions'))]);
    expect(first).toEqual({ id: 'me', name: 'Aysel' });
    expect(second).toEqual({ id: 'me', name: 'Aysel' });
    const refreshCalls = fetchMock.mock.calls.filter(([input]) =>
      (typeof input === 'string' ? input : input.url).endsWith('/api/v1/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });

  it('yeniləmə alınmırsa, ilkin 401 qaytarılır', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ code: 'SESSION_EXPIRED' }, 401)));
    const error = await unwrap(api.GET('/api/v1/me')).catch((e: unknown) => e);
    expect((error as { status: number }).status).toBe(401);
  });

  it('giriş axınının özündə yeniləmə edilmir', async () => {
    const fetchMock = vi.fn(async () => json({ code: 'OTP_INVALID' }, 400));
    vi.stubGlobal('fetch', fetchMock);
    await unwrap(api.POST('/api/v1/auth/otp/verify', { body: { challengeId: 'x', code: '123456' } })).catch(() => undefined);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
