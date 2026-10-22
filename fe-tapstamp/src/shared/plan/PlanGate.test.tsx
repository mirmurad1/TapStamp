import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { customerI18n } from '@/customer/i18n';

import { PlanGate } from './PlanGate';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

function renderGate(audience: 'staff' | 'customer') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PlanGate audience={audience} loading={<p>loading</p>}>
        <p>tətbiq</p>
      </PlanGate>
    </QueryClientProvider>,
  );
}

describe('PlanGate', () => {
  beforeAll(async () => {
    await customerI18n.changeLanguage('az');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sınaq bitibsə tətbiqin yerinə dayandırılma ekranı', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ plan: 'PREMIUM', status: 'TRIAL', expired: true, features: [] })));
    renderGate('customer');
    expect(await screen.findByRole('heading', { name: 'Xidmət müvəqqəti dayandırılıb' })).toBeInTheDocument();
    expect(screen.queryByText('tətbiq')).not.toBeInTheDocument();
  });

  it('aktiv paketdə tətbiq açılır', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ plan: 'BASIC', status: 'ACTIVE', expired: false, features: [] })));
    renderGate('staff');
    expect(await screen.findByText('tətbiq')).toBeInTheDocument();
  });

  it('platforma domenində (paket yoxdur — 404) tətbiq açılır', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ code: 'NOT_FOUND' }, 404)));
    renderGate('staff');
    expect(await screen.findByText('tətbiq')).toBeInTheDocument();
  });
});
