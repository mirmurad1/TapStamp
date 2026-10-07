import { expect, test, type Page } from '@playwright/test';

import { customerSignUp, staffLogin } from './support';

/**
 * Tenant izolyasiyası (hər şirkətin öz bazası və domeni): bir şirkətin sessiyası, hədiyyə kartı və NFC linki digər
 * şirkətdə işləmir.
 */
const OTHER = 'https://demo-cay.localhost';

async function api(page: Page, method: string, url: string, body?: unknown, headers: Record<string, string> = {}) {
  return page.evaluate(
    async ({ method: m, url: u, body: b, headers: h }) => {
      const csrf = document.cookie.split('; ').find((c) => c.startsWith('__Host-XSRF-TOKEN='))?.slice('__Host-XSRF-TOKEN='.length) ?? '';
      const response = await fetch(u, {
        method: m,
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrf), ...h },
        body: b === undefined ? undefined : JSON.stringify(b),
      });
      return { status: response.status, body: (await response.json().catch(() => null)) as Record<string, unknown> | null };
    },
    { method, url, body, headers },
  );
}

test('bir şirkətin sessiyası və hədiyyə kartı digər şirkətdə işləmir', async ({ browser }) => {
  // Mock Coffee müştərisi və onun hədiyyə kartı (sahib möhürləri əl ilə əlavə edir)
  const customer = await (await browser.newContext()).newPage();
  await customerSignUp(customer, 'İzolyasiya Testi');
  const me = (await (await customer.request.get('/api/v1/me')).json()) as { id: string; card: { stampsRequired: number } };

  const owner = await (await browser.newContext()).newPage();
  await staffLogin(owner, 'owner@mockcoffee.az');
  const branches = (await (await owner.request.get('/api/v1/public/company')).json()) as { branches: { id: string }[] };
  const adjusted = await api(
    owner,
    'POST',
    `/api/v1/admin/customers/${me.id}/stamp-adjustments`,
    { delta: me.card.stampsRequired, reason: 'E2E izolyasiya testi', branchId: branches.branches[0]?.id },
    { 'Idempotency-Key': `e2e-${Date.now()}` },
  );
  expect(adjusted.status).toBe(200);
  const cards = (await (await customer.request.get('/api/v1/me/gift-cards')).json()) as { code: string }[];
  const code = cards[0]?.code ?? '';
  expect(code).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}$/);

  // Öz şirkətində kod tapılır
  expect((await api(owner, 'POST', '/api/v1/admin/staff-console/gift-cards/check', { code })).status).toBe(200);

  // Digər şirkətin işçisi eyni kodu tapa bilmir (başqa baza)
  const otherOwner = await (await browser.newContext({ baseURL: OTHER })).newPage();
  await staffLogin(otherOwner, 'owner@democay.az');
  const foreign = await api(otherOwner, 'POST', '/api/v1/admin/staff-console/gift-cards/check', { code });
  expect(foreign.status).toBe(404);
  expect(foreign.body?.code).toBe('GIFT_CARD_NOT_FOUND');

  // Müştərinin və işçinin sessiya cookie-ləri digər domenə köçürülsə də keçərsiz olur
  for (const page of [customer, owner]) {
    const cookies = await page.context().cookies();
    const stolen = await browser.newContext({ baseURL: OTHER });
    await stolen.addCookies(cookies.map((c) => ({ ...c, domain: 'demo-cay.localhost', url: undefined })));
    const meResponse = await stolen.request.get('/api/v1/me');
    const adminResponse = await stolen.request.get('/api/v1/admin/me');
    expect(meResponse.status()).toBe(401);
    expect(adminResponse.status()).toBe(401);
    await stolen.close();
  }
});

test('bir şirkətin NFC linki digər şirkətin domenində qəbul olunmur', async ({ browser }) => {
  const staff = await (await browser.newContext()).newPage();
  await staffLogin(staff, 'barista@mockcoffee.az');
  await staff.goto('/admin/dev/simulator');
  await staff.locator('main button:has(svg.lucide-nfc)').click();
  const url = ((await staff.getByTestId('simulator-url').textContent()) ?? '').trim();
  const foreign = url.replace('mockcoffee.localhost', 'demo-cay.localhost');

  const phone = await (await browser.newContext()).newPage();
  await phone.goto(foreign);
  await expect(phone).toHaveURL(/\/tap\/error\?code=TAG_UNKNOWN$/);
});
