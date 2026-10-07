import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { installCookieJar } from '@/test/cookieJar';

import { createCustomerQueryClient } from './auth/queryClient';
import { customerI18n } from './i18n';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let App: typeof import('./App').App;
let customerRouter: typeof import('./router').customerRouter;

beforeAll(async () => {
  window.history.replaceState(null, '', '/');
  ({ App } = await import('./App'));
  ({ customerRouter } = await import('./router'));
}, 60_000);

describe('hədiyyə kartları', () => {
  let restoreCookies: () => void;

  beforeEach(async () => {
    restoreCookies = installCookieJar({ '__Host-XSRF-TOKEN': 'csrf' });
    await customerI18n.changeLanguage('az');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    restoreCookies();
  });

  it('kod göstərilərkən işçi təhvil verəndə ekranda təbrik görünür; bitməyə yaxın kartda xatırlatma', async () => {
    const user = userEvent.setup();
    let redeemed = false;
    const soon = new Date(Date.now() + 3 * 86_400_000).toISOString();
    vi.stubGlobal('fetch', vi.fn(async (input: Request) => {
      switch (`${input.method} ${new URL(input.url).pathname}`) {
        case 'GET /api/v1/public/company':
          return json({ name: { az: 'Moyka Pro' }, branches: [] });
        case 'GET /api/v1/auth/session':
          return json({ state: 'ACTIVE' });
        case 'GET /api/v1/me':
          return json({ id: 'u1', preferredLanguage: 'az', availableRewards: [], pendingConsents: [] });
        case 'GET /api/v1/me/gift-cards':
          return json([{ id: 'g1', code: 'Q85J-QMJW', name: { az: 'Pulsuz yuma' }, status: redeemed ? 'REDEEMED' : 'AVAILABLE',
            earnedAt: new Date().toISOString(), expiresAt: soon, redeemedAt: redeemed ? new Date().toISOString() : undefined }]);
        default:
          return json({ code: 'NOT_FOUND' }, 404);
      }
    }));
    await customerRouter.navigate('/gift-cards');
    render(
      <QueryClientProvider client={createCustomerQueryClient()}>
        <App />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('3 gün qalıb')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Kodu göstər' }));
    expect(await screen.findByTestId('gift-card-code')).toHaveTextContent('Q85J-QMJW');
    expect(screen.getByRole('img', { name: 'Hədiyyə kartının QR kodu' })).toBeInTheDocument();

    redeemed = true;
    expect(await screen.findByRole('heading', { name: 'Hədiyyəniz təhvil verildi! 🎉' }, { timeout: 6_000 })).toBeInTheDocument();
  });
});
