import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
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
  name: { az: 'Moyka Pro' },
  brandColor: '#0EA5E9',
  branches: [],
  rewardRule: { stampsRequired: 5, rewardName: { az: 'Pulsuz yuma' } },
};

let App: typeof import('./App').App;
let customerRouter: typeof import('./router').customerRouter;

beforeAll(async () => {
  window.history.replaceState(null, '', '/');
  ({ App } = await import('./App'));
  ({ customerRouter } = await import('./router'));
}, 60_000);

function mockBackend(routes: Record<string, () => Response>) {
  const calls: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: Request) => {
    const key = `${input.method} ${new URL(input.url).pathname}`;
    calls.push(key);
    return routes[key]?.() ?? json({ code: 'NOT_FOUND' }, 404);
  }));
  return calls;
}

async function renderAt(path: string) {
  await customerRouter.navigate(path);
  render(
    <QueryClientProvider client={createCustomerQueryClient()}>
      <App />
    </QueryClientProvider>,
  );
}

describe('NFC toxunuşu səhifəsi', () => {
  let restoreCookies: () => void;

  beforeEach(async () => {
    restoreCookies = installCookieJar({ '__Host-XSRF-TOKEN': 'csrf' });
    await customerI18n.changeLanguage('az');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    restoreCookies();
  });

  it('daxil olmuş müştəri: möhür avtomatik alınır, kart dolanda təbrik və hədiyyə kartı', async () => {
    const calls = mockBackend({
      'GET /api/v1/public/company': () => json(COMPANY),
      'GET /api/v1/auth/session': () => json({ state: 'ACTIVE' }),
      'GET /api/v1/me': () => json({ id: 'u1', name: 'Aysel', preferredLanguage: 'az', card: { currentStamps: 0, stampsRequired: 5 },
        availableRewards: [], pendingConsents: [] }),
      'GET /api/v1/taps/tok-123': () => json({ status: 'ACTIVE', branchName: { az: 'Mərkəz' }, sameBrowser: true }),
      'POST /api/v1/taps/tok-123/claim': () => json({ stampId: 's1', status: 'CONFIRMED', currentStamps: 0, stampsRequired: 5,
        giftCardId: 'g1' }),
    });
    await renderAt('/tap/tok-123');

    expect(await screen.findByRole('heading', { name: 'Möhür əlavə olundu!', hidden: true })).toBeInTheDocument();
    // Təbrik dialoqu möhür animasiyasından (~2,2 san) sonra açılır
    expect(await screen.findByRole('heading', { name: /Təbriklər!/ }, { timeout: 4000 })).toBeInTheDocument();
    expect(screen.getByText('Kartınız doldu — Pulsuz yuma sizindir!')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Hədiyyə kartına bax' })[0]).toHaveAttribute('href', '/gift-cards');
    expect(calls.filter((call) => call === 'POST /api/v1/taps/tok-123/claim')).toHaveLength(1);
    // Brend rəngi tətbiq olunub
    expect(document.documentElement.style.getPropertyValue('--brand-dark')).toBe('#0ea5e9');
  });

  it('qonaq: girişə yönəldilir, toxunuş tokeni ilə', async () => {
    mockBackend({
      'GET /api/v1/public/company': () => json(COMPANY),
      'GET /api/v1/auth/session': () => json({ state: 'NONE' }),
      'GET /api/v1/taps/tok-guest-0123456789abc': () => json({ status: 'ACTIVE', branchName: { az: 'Mərkəz' }, sameBrowser: true }),
    });
    await renderAt('/tap/tok-guest-0123456789abc');

    expect(await screen.findByRole('link', { name: 'Daxil ol və möhürü al' })).toHaveAttribute(
      'href',
      '/login?tap=tok-guest-0123456789abc',
    );
  });

  it('cooldown xətası geri sayımla göstərilir', async () => {
    mockBackend({
      'GET /api/v1/public/company': () => json(COMPANY),
      'GET /api/v1/auth/session': () => json({ state: 'ACTIVE' }),
      'GET /api/v1/me': () => json({ id: 'u1', preferredLanguage: 'az', availableRewards: [], pendingConsents: [] }),
      'GET /api/v1/taps/tok-cd': () => json({ status: 'ACTIVE', sameBrowser: true }),
      'POST /api/v1/taps/tok-cd/claim': () => json({ code: 'STAMP_COOLDOWN_ACTIVE', status: 409, retryAfterSeconds: 840 }, 409),
    });
    await renderAt('/tap/tok-cd');

    expect(await screen.findByText('Növbəti möhür üçün bir az gözləyin.')).toBeInTheDocument();
    expect(screen.getByText(/^1[34]:\d\d$/)).toBeInTheDocument();
  });

  it('backend-in yönləndirdiyi xəta səhifəsi', async () => {
    mockBackend({
      'GET /api/v1/public/company': () => json(COMPANY),
      'GET /api/v1/auth/session': () => json({ state: 'NONE' }),
    });
    await renderAt('/tap/error?code=TAP_REPLAYED');

    expect(await screen.findByText('Bu link artıq istifadə olunub. Hər toxunuş yalnız bir dəfə keçərlidir.')).toBeInTheDocument();
  });
});
