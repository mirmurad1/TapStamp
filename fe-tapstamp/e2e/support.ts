import { createHmac } from 'node:crypto';

import { expect, type APIRequestContext, type Browser, type Page } from '@playwright/test';

/** E2E köməkçiləri: dev hesabları (README-dəki), TOTP, SMS kodu, simulyator. Yalnız dev mühiti üçün. */
export const DEV_PASSWORD = process.env.DEV_SEED_ADMIN_PASSWORD ?? 'Kofe-Dev-Parol-2026';
const DEV_TOTP_SECRET = Buffer.from('tapstamp-dev-totpkey', 'ascii');

export function uniquePhone(): string {
  return `055${String(Math.floor(Math.random() * 10_000_000)).padStart(7, '0')}`;
}

function totp(step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = createHmac('sha1', DEV_TOTP_SECRET).update(counter).digest();
  const offset = (hmac[19] ?? 0) & 0xf;
  const code =
    (((hmac[offset] ?? 0) & 0x7f) << 24) |
    ((hmac[offset + 1] ?? 0) << 16) |
    ((hmac[offset + 2] ?? 0) << 8) |
    (hmac[offset + 3] ?? 0);
  return String(code % 1_000_000).padStart(6, '0');
}

export async function staffLogin(page: Page, email: string): Promise<void> {
  await page.goto('/admin/login');
  await page.getByLabel('Email').fill(email);
  await page.locator('input[type="password"]').fill(DEV_PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/admin(\/mfa)?\/?$/);
  if (page.url().endsWith('/mfa')) {
    // Eyni TOTP addımı iki dəfə qəbul olunmur — növbəti addımın kodu işlədilir (server ±1 addım qəbul edir). Kod artıq
    // istifadə olunubsa (əvvəlki testin girişi eyni addımda), növbəti 30 saniyəlik addım gözlənilib təkrar cəhd edilir
    for (let attempt = 0; attempt < 3; attempt++) {
      await page.locator('input[autocomplete="one-time-code"]').fill(totp(Math.floor(Date.now() / 30_000) + 1));
      await page.locator('form button[type="submit"]').click();
      const done = await page.waitForURL(/\/admin\/?$/, { timeout: 5_000 }).then(
        () => true,
        () => false,
      );
      if (done) {
        return;
      }
      await page.waitForTimeout(30_000 - (Date.now() % 30_000) + 500);
    }
    throw new Error('TOTP login failed');
  }
}

/** Sahibin parametrlərini brauzer sessiyası ilə dəyişir (CSRF cookie-dən). Əvvəlki dəyəri qaytarır. */
export async function putStampPolicy(page: Page, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return page.evaluate(async (policy) => {
    const before = (await (await fetch('/api/v1/admin/settings/stamp-policy')).json()) as Record<string, unknown>;
    // Token cookie-dən dəyişdirici sorğudan dərhal əvvəl oxunur (əvvəlki cavab onu yeniləyə bilər)
    const csrf = document.cookie.split('; ').find((c) => c.startsWith('__Host-XSRF-TOKEN='))?.slice('__Host-XSRF-TOKEN='.length) ?? '';
    const response = await fetch('/api/v1/admin/settings/stamp-policy', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrf) },
      body: JSON.stringify(policy),
    });
    if (!response.ok) {
      throw new Error(`stamp-policy ${response.status} ${await response.text()}`);
    }
    return before;
  }, body);
}

export async function simulatorTap(page: Page, uid: string): Promise<string> {
  if (!page.url().endsWith('/admin/dev/simulator')) {
    await page.goto('/admin/dev/simulator');
  }
  const select = page.locator('#simulator-tag');
  await select.selectOption((await select.locator('option', { hasText: uid }).getAttribute('value')) ?? '');
  const existing = page.getByTestId('simulator-url');
  const previous = (await existing.count()) > 0 ? await existing.textContent() : null;
  await page.locator('main button:has(svg.lucide-nfc)').click();
  const link = page.getByTestId('simulator-url');
  if (previous) {
    await expect(link).not.toHaveText(previous);
  }
  const url = (await link.textContent())?.trim() ?? '';
  expect(url).toContain(`/t/${uid}?picc=`);
  return url;
}

export async function latestSmsCode(request: APIRequestContext, phone: string): Promise<string> {
  const e164 = `+994${phone.slice(1)}`;
  for (let attempt = 0; attempt < 20; attempt++) {
    const entries = (await (await request.get('/api/v1/dev/outbox?channel=SMS')).json()) as { to: string; body: string }[];
    const match = entries.find((entry) => entry.to === e164)?.body.match(/\b(\d{6})\b/);
    if (match?.[1]) {
      return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('SMS code not found in dev outbox');
}

/** Toxunuş linkindən başlayaraq yeni müştərinin qeydiyyatı (telefon konteksti). */
export async function registerFromTap(page: Page, tapUrl: string, name: string): Promise<void> {
  await page.goto(tapUrl);
  await page.getByRole('link', { name: 'Daxil ol və möhürü al' }).click();
  const number = uniquePhone();
  await page.getByLabel('Telefon nömrəsi').fill(number);
  await page.getByRole('button', { name: 'Kod göndər' }).click();
  await page.getByLabel('Təsdiq kodu').fill(await latestSmsCode(page.context().request, number));
  await page.getByRole('button', { name: 'Təsdiqlə' }).click();
  await page.getByLabel('Adınız').fill(name);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();
}

export async function phoneContext(browser: Browser) {
  return browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, locale: 'az-AZ' });
}

/** Yeni müştərinin birbaşa giriş səhifəsindən qeydiyyatı (toxunuşsuz). */
export async function customerSignUp(page: Page, name: string): Promise<void> {
  await page.goto('/login');
  const number = uniquePhone();
  await page.getByLabel('Telefon nömrəsi').fill(number);
  await page.getByRole('button', { name: 'Kod göndər' }).click();
  await page.getByLabel('Təsdiq kodu').fill(await latestSmsCode(page.context().request, number));
  await page.getByRole('button', { name: 'Təsdiqlə' }).click();
  await page.getByLabel('Adınız').fill(name);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login') && !url.pathname.startsWith('/register'));
}
