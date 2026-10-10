import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { installCookieJar } from '@/test/cookieJar';

import { createCustomerQueryClient } from './auth/queryClient';
import { customerI18n } from './i18n';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

const COMPANY = {
  name: { az: 'Mock Coffee House' },
  description: { az: 'Fəvvarələr meydanının yanında.' },
  branches: [{ id: 'b1', name: { az: 'Mərkəz filialı' }, address: { az: 'Nizami küçəsi 10' } }],
  rewardRule: { stampsRequired: 9, rewardName: { az: 'Hədiyyə qəhvə' } },
};

let App: typeof import('./App').App;

// Modulun ilk yüklənməsi yavaş maşında 10 saniyədən uzun çəkə bilər
beforeAll(async () => {
  window.history.replaceState(null, '', '/');
  ({ App } = await import('./App'));
}, 60_000);

describe('müştəri tətbiqi', () => {
  let restoreCookies: () => void;

  beforeEach(async () => {
    restoreCookies = installCookieJar({ '__Host-XSRF-TOKEN': 'csrf' });
    await customerI18n.changeLanguage('az');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    restoreCookies();
  });

  it('yeni müştəri: telefon → SMS kodu → razılıqla qeydiyyat → möhür kartı', async () => {
    const user = userEvent.setup();
    let signedIn = false;
    const requests: { path: string; body: unknown }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: Request) => {
      const url = new URL(input.url);
      const body = input.method === 'GET' ? undefined : await input.clone().text().then((t) => (t ? JSON.parse(t) : undefined));
      requests.push({ path: `${input.method} ${url.pathname}`, body });
      switch (`${input.method} ${url.pathname}`) {
        case 'GET /api/v1/public/company':
          return json(COMPANY);
        case 'GET /api/v1/auth/session':
          return json({ state: signedIn ? 'ACTIVE' : 'NONE' });
        case 'POST /api/v1/auth/otp/request':
          return json({ challengeId: 'c1', expiresAt: new Date(Date.now() + 300_000).toISOString(),
            resendAvailableAt: new Date(Date.now() + 60_000).toISOString() }, 202);
        case 'POST /api/v1/auth/otp/verify':
          return json({ status: 'REGISTRATION_REQUIRED', registrationToken: 'reg-token' });
        case 'GET /api/v1/public/legal':
          return json([{ type: 'PRIVACY_POLICY', slug: 'privacy', version: 1 }, { type: 'TERMS', slug: 'terms', version: 2 }]);
        case 'POST /api/v1/auth/register':
          signedIn = true;
          return json({ status: 'AUTHENTICATED' }, 201);
        case 'GET /api/v1/me':
          return json({ id: 'u1', name: 'Aysel', preferredLanguage: 'az', phone: '+994501234567',
            card: { currentStamps: 3, stampsRequired: 9, rewardName: { az: 'Hədiyyə qəhvə' } },
            availableRewards: [], pendingConsents: [] });
        default:
          return json({ code: 'NOT_FOUND' }, 404);
      }
    }));

    render(
      <QueryClientProvider client={createCustomerQueryClient()}>
        <App />
      </QueryClientProvider>,
    );

    // Qonaq: kafe haqqında məlumat və giriş düyməsi
    await screen.findByRole('heading', { name: 'Mock Coffee House' });
    expect(screen.getByText('9 möhür toplayın — Hədiyyə qəhvə bizdən!')).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Daxil ol və möhür topla' }));

    await user.type(await screen.findByLabelText('Telefon nömrəsi'), '050 123 45 67');
    await user.click(screen.getByRole('button', { name: 'Kod göndər' }));

    await screen.findByText('050 123 45 67 nömrəsinə kod göndərdik.');
    expect(screen.getByRole('button', { name: /saniyədən sonra/ })).toBeDisabled();
    await user.type(screen.getByLabelText('Təsdiq kodu'), '123456');
    await user.click(screen.getByRole('button', { name: 'Təsdiqlə' }));

    await screen.findByRole('heading', { name: 'Qeydiyyatı tamamlayın' });
    await user.type(screen.getByLabelText('Adınız'), 'Aysel');
    await user.click(screen.getByRole('button', { name: 'Qeydiyyatı tamamla' }));
    expect(await screen.findByText('Davam etmək üçün razılıq vermək lazımdır.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'məxfilik siyasəti' })).toHaveAttribute('href', '/legal/privacy');

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Qeydiyyatı tamamla' }));

    await screen.findByRole('heading', { name: 'Salam, Aysel!' });
    expect(screen.getByText('Hədiyyəyə 6 möhür qalıb')).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: 'Möhür vurulub' })).toHaveLength(3);
    // İlk girişdən sonra: nömrə bir daha soruşulmayacaq; başqa nömrəyə keçid Parametrlərdədir
    expect(screen.getByText('Nömrənizi bir daha soruşmayacağıq.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Fərqli nömrə ilə yoxla' })).not.toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/'));

    const register = requests.find((request) => request.path === 'POST /api/v1/auth/register');
    expect(register?.body).toEqual({
      registrationToken: 'reg-token',
      name: 'Aysel',
      acceptedDocuments: [{ type: 'PRIVACY_POLICY', version: 1 }, { type: 'TERMS', version: 2 }],
      language: 'az',
    });
    const otp = requests.find((request) => request.path === 'POST /api/v1/auth/otp/request');
    expect(otp?.body).toEqual({ phone: '050 123 45 67', language: 'az' });
  });

  it('hesabın silinməsi: SMS kodu ilə təsdiq → silinmə → qonaq ana səhifəsi və bildiriş', async () => {
    const user = userEvent.setup();
    let signedIn = true;
    const calls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      calls.push(key);
      switch (key) {
        case 'GET /api/v1/public/company':
          return json(COMPANY);
        case 'GET /api/v1/auth/session':
          return json({ state: signedIn ? 'ACTIVE' : 'NONE' });
        case 'GET /api/v1/me':
          return signedIn
            ? json({ id: 'u1', name: 'Aysel', preferredLanguage: 'az', phone: '+994501234567',
              card: { currentStamps: 3, stampsRequired: 9 }, availableRewards: [], pendingConsents: [] })
            : json({ code: 'UNAUTHENTICATED' }, 401);
        case 'GET /api/v1/me/sessions':
          return json([{ id: 's1', deviceLabel: 'Chrome · Android', current: true }]);
        case 'POST /api/v1/auth/step-up/otp/request':
          return json({ challengeId: 'c2' }, 202);
        case 'POST /api/v1/auth/step-up/otp/verify':
          return new Response(null, { status: 204 });
        case 'DELETE /api/v1/me':
          signedIn = false;
          return new Response(null, { status: 204 });
        default:
          return json({ code: 'NOT_FOUND' }, 404);
      }
    }));
    window.history.replaceState(null, '', '/settings');
    const { customerRouter } = await import('./router');
    await customerRouter.navigate('/settings');

    render(
      <QueryClientProvider client={createCustomerQueryClient()}>
        <App />
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole('button', { name: 'Hesabı sil' }));
    await user.click(await screen.findByRole('button', { name: 'SMS kodu göndər' }));
    await user.type(await screen.findByLabelText('Təsdiq kodu'), '123456');
    await user.click(screen.getByRole('button', { name: 'Hesabı birdəfəlik sil' }));

    expect(await screen.findByText('Hesabınız silindi. Sizi yenidən gözləyirik!')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/');
    expect(screen.getByRole('link', { name: 'Daxil ol və möhür topla' })).toBeInTheDocument();
    expect(calls.indexOf('POST /api/v1/auth/step-up/otp/verify')).toBeLessThan(calls.indexOf('DELETE /api/v1/me'));
  });

  it('səhifə yenidən açılanda izah görünmür; Parametrlərdəki "Fərqli nömrə ilə yoxla" çıxış edib girişə aparır', async () => {
    const user = userEvent.setup();
    let signedIn = true;
    const calls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`;
      calls.push(key);
      switch (key) {
        case 'GET /api/v1/public/company':
          return json(COMPANY);
        case 'GET /api/v1/auth/session':
          return json({ state: signedIn ? 'ACTIVE' : 'NONE' });
        case 'GET /api/v1/me':
          return json({ id: 'u1', name: 'Aysel', preferredLanguage: 'az', card: { currentStamps: 2, stampsRequired: 9 },
            availableRewards: [], pendingConsents: [] });
        case 'GET /api/v1/me/sessions':
          return json([]);
        case 'POST /api/v1/auth/logout':
          signedIn = false;
          return new Response(null, { status: 204 });
        default:
          return json({ code: 'NOT_FOUND' }, 404);
      }
    }));
    const { customerRouter } = await import('./router');
    await customerRouter.navigate('/');

    render(
      <QueryClientProvider client={createCustomerQueryClient()}>
        <App />
      </QueryClientProvider>,
    );

    await screen.findByRole('heading', { name: 'Salam, Aysel!' });
    expect(screen.queryByText('Nömrənizi bir daha soruşmayacağıq.')).not.toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Parametrlər' }));
    await user.click(screen.getByRole('button', { name: 'Fərqli nömrə ilə yoxla' }));

    expect(await screen.findByLabelText('Telefon nömrəsi')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
    expect(calls).toContain('POST /api/v1/auth/logout');
  });
});
