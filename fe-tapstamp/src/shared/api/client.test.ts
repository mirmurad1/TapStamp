import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { installCookieJar } from '@/test/cookieJar';

import { api, ApiError, CSRF_COOKIE, CSRF_HEADER, readCookie, unwrap } from './client';

describe('API klienti', () => {
  let restoreCookies: () => void;

  beforeEach(() => {
    restoreCookies = installCookieJar({ [CSRF_COOKIE]: 'token-123' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    restoreCookies();
  });

  it('dəyişdirici sorğulara CSRF başlığını əlavə edir, GET sorğularına yox', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ status: 'AUTHENTICATED' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await unwrap(api.POST('/api/v1/admin/auth/login', { body: { email: 'a@b.az', password: 'x' } }));
    await unwrap(api.GET('/api/v1/admin/auth/session'));

    const [post, get] = fetchMock.mock.calls.map((call) => (call as unknown as [Request])[0]);
    expect(post?.headers.get(CSRF_HEADER)).toBe('token-123');
    expect(get?.headers.get(CSRF_HEADER)).toBeNull();
  });

  it('Problem Details cavabını kodu olan ApiError-a çevirir', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      JSON.stringify({ code: 'LOGIN_FAILED', status: 401, title: 'x' }),
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
    )));

    const error = await unwrap(api.POST('/api/v1/admin/auth/login', { body: { email: 'a@b.az', password: 'x' } }))
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('LOGIN_FAILED');
    expect((error as ApiError).status).toBe(401);
  });

  it('şəbəkə xətası NETWORK_ERROR kimi qaytarılır', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const error = await unwrap(api.GET('/api/v1/admin/auth/session')).catch((e: unknown) => e);
    expect((error as ApiError).code).toBe('NETWORK_ERROR');
  });

  it('cookie-ni adına görə oxuyur', () => {
    expect(readCookie('lang', 'a=1; lang=ru; b=2')).toBe('ru');
    expect(readCookie('missing', 'a=1')).toBeUndefined();
  });
});
