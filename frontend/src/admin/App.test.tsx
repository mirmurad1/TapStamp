import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { installCookieJar } from '@/test/cookieJar';

import { adminI18n } from './i18n';
import { createAdminQueryClient } from './auth/session';

type Handler = (request: Request) => Response | Promise<Response>;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

/** Sadə backend imitasiyası: sessiyanın vəziyyəti və giriş cavabı test daxilində idarə olunur. */
function mockBackend(routes: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: Request) => {
    const url = new URL(input.url);
    const handler = routes[`${input.method} ${url.pathname}`];
    return handler ? handler(input) : json({ code: 'NOT_FOUND' }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

let App: typeof import('./App').App;

beforeAll(async () => {
  window.history.replaceState(null, '', '/admin/');
  ({ App } = await import('./App'));
});

describe('admin girişi', () => {
  let session: string;
  let restoreCookies: () => void;

  beforeEach(async () => {
    session = 'NONE';
    restoreCookies = installCookieJar({ '__Host-XSRF-TOKEN': 'test-csrf' });
    await adminI18n.changeLanguage('az');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    restoreCookies();
  });

  function renderApp() {
    return render(
      <QueryClientProvider client={createAdminQueryClient()}>
        <App />
      </QueryClientProvider>,
    );
  }

  it('daxil olmamış istifadəçini girişə yönləndirir və xətanı seçilmiş dildə göstərir', async () => {
    const user = userEvent.setup();
    const fetchMock = mockBackend({
      'GET /api/v1/context': () => json({ scope: 'TENANT', tenant: { slug: 'mockcoffee', defaultLanguage: 'az' } }),
      'GET /api/v1/public/company': () => json({ name: { az: 'Mock Coffee House' }, branches: [] }),
      'GET /api/v1/admin/auth/session': () => json({ state: session }),
      'POST /api/v1/admin/auth/login': () => json({ code: 'LOGIN_FAILED', status: 401 }, 401),
    });
    renderApp();

    await screen.findByRole('heading', { name: 'İdarəetmə panelinə giriş' });
    expect(window.location.pathname).toBe('/admin/login');

    // Klient tərəfi validasiyası: boş forma göndərilmir
    await user.click(screen.getByRole('button', { name: 'Daxil ol' }));
    expect(await screen.findAllByText('Bu sahə məcburidir.')).toHaveLength(2);

    await user.type(screen.getByLabelText('Email'), 'owner@mockcoffee.az');
    await user.type(screen.getByLabelText('Şifrə'), 'yanlış-şifrə-123');
    await user.click(screen.getByRole('button', { name: 'Daxil ol' }));
    expect(await screen.findByText('Email və ya şifrə yanlışdır.')).toBeInTheDocument();
    // Şifrə sahəsi təmizlənir, email qalır
    expect(screen.getByLabelText('Şifrə')).toHaveValue('');
    expect(screen.getByLabelText('Email')).toHaveValue('owner@mockcoffee.az');

    await user.click(screen.getByRole('radio', { name: 'EN' }));
    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument();

    const login = fetchMock.mock.calls
      .map((call) => (call as unknown as [Request])[0])
      .find((request) => request.url.endsWith('/auth/login'));
    expect(login?.headers.get('X-XSRF-TOKEN')).toBe('test-csrf');
  });

  it('düzgün şifrədən sonra TOTP addımına keçir', async () => {
    const user = userEvent.setup();
    mockBackend({
      'GET /api/v1/context': () => json({ scope: 'TENANT', tenant: { slug: 'mockcoffee', defaultLanguage: 'az' } }),
      'GET /api/v1/public/company': () => json({ name: { az: 'Mock Coffee House' }, branches: [] }),
      'GET /api/v1/admin/auth/session': () => json({ state: session }),
      'POST /api/v1/admin/auth/login': () => {
        session = 'PENDING_MFA';
        return json({ status: 'MFA_REQUIRED' });
      },
      'POST /api/v1/admin/auth/mfa/verify': () => json({ code: 'MFA_INVALID', status: 401 }, 401),
    });
    renderApp();

    await user.type(await screen.findByLabelText('Email'), 'owner@mockcoffee.az');
    await user.type(screen.getByLabelText('Şifrə'), 'Kofe-Dev-Parol-2026');
    await user.click(screen.getByRole('button', { name: 'Daxil ol' }));

    await screen.findByRole('heading', { name: 'İki addımlı yoxlama' });
    await waitFor(() => expect(window.location.pathname).toBe('/admin/mfa'));

    await user.type(screen.getByLabelText('Təsdiq kodu'), '123456');
    await user.click(screen.getByRole('button', { name: 'Təsdiqlə' }));
    expect(await screen.findByText('Kod yanlışdır. Tətbiqdəki ən son kodu daxil edin.')).toBeInTheDocument();
    // Səhv koddan sonra MFA səhifəsində qalır (sessiya hələ etibarlıdır)
    expect(window.location.pathname).toBe('/admin/mfa');
  });

  it('"başqa hesabla daxil ol" sessiyanı bağlayır və giriş səhifəsinə qaytarır', async () => {
    const user = userEvent.setup();
    session = 'PENDING_MFA';
    const fetchMock = mockBackend({
      'GET /api/v1/context': () => json({ scope: 'TENANT', tenant: { slug: 'mockcoffee', defaultLanguage: 'az' } }),
      'GET /api/v1/public/company': () => json({ name: { az: 'Mock Coffee House' }, branches: [] }),
      'GET /api/v1/admin/auth/session': () => json({ state: session }),
      'POST /api/v1/admin/auth/logout': () => {
        session = 'NONE';
        return new Response(null, { status: 204 });
      },
    });
    renderApp();

    await screen.findByRole('heading', { name: 'İki addımlı yoxlama' });
    await user.click(screen.getByRole('button', { name: 'Başqa hesabla daxil ol' }));

    await screen.findByRole('heading', { name: 'İdarəetmə panelinə giriş' });
    expect(window.location.pathname).toBe('/admin/login');
    expect(fetchMock.mock.calls.some((call) => (call as unknown as [Request])[0].url.endsWith('/auth/logout'))).toBe(true);
  });
});
