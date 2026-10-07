// Admin panelinin səhifələri (sahib kimi) — Mərhələ 7 ekran görüntüləri. Heç nə dəyişdirilmir.
// İstifadə: node scripts/screenshots/admin-pages.cjs [qovluq] [prefiks]
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'frontend/node_modules/@playwright/test'));
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase7';
const base = 'https://mockcoffee.localhost';

// [fayl adı, yol, gözlənilən mətn, tam səhifə]
const PAGES = [
  ['01-dashboard', '/admin', 'Ən sıx saatlar', true],
  ['02-dashboard-mobile', '/admin', 'Ən sıx saatlar', false, { width: 390, height: 844 }],
  ['03-customers', '/admin/customers', 'Son gəliş', false],
  ['06-tags', '/admin/tags', 'Son toxunuş', true],
  ['07-audit', '/admin/audit', 'İcraçı', false],
  ['08-settings', '/admin/settings', 'Filiallar', true],
];

function totp(step) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = crypto.createHmac('sha1', Buffer.from('cafe-loyalty-dev-key', 'ascii')).update(counter).digest();
  const o = hmac[19] & 0xf;
  return String((((hmac[o] & 0x7f) << 24) | (hmac[o + 1] << 16) | (hmac[o + 2] << 8) | hmac[o + 3]) % 1e6).padStart(6, '0');
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'az-AZ', viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  try {
    await page.goto(`${base}/admin/login`);
    await page.getByLabel('Email').fill('owner@mockcoffee.az');
    await page.locator('input[type="password"]').fill('Kofe-Dev-Parol-2026');
    await page.locator('form button[type="submit"]').click();
    await page.waitForURL(/\/admin(\/mfa)?\/?$/);
    if (page.url().endsWith('/mfa')) {
      await page.locator('input[autocomplete="one-time-code"]').fill(totp(Math.floor(Date.now() / 30000)));
      await page.locator('form button[type="submit"]').click();
      await page.waitForURL(/\/admin\/?$/);
    }
    for (const [name, route, text, fullPage, viewport] of PAGES) {
      await page.setViewportSize(viewport ?? { width: 1280, height: 900 });
      await page.goto(base + route);
      await page.getByText(text).first().waitFor();
      await page.waitForTimeout(800);
      await page.screenshot({ path: `${out}/${prefix}-${name}.png`, fullPage });
    }
    // Müştərinin detalları və əl ilə düzəliş dialoqu
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(base + '/admin/customers');
    await page.locator('tbody a').first().click();
    await page.getByText('Tarixçə').first().waitFor();
    await page.waitForTimeout(600);
    await page.screenshot({ path: `${out}/${prefix}-04-customer-detail.png`, fullPage: true });
    await page.getByRole('button', { name: 'Möhürü düzəlt' }).click();
    await page.getByRole('dialog').waitFor();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${out}/${prefix}-05-customer-adjust.png` });
    await page.keyboard.press('Escape');
    // Filialın düzəlişi: ad/ünvan 3 dildə, iş saatları
    await page.goto(base + '/admin/settings');
    await page.getByRole('button', { name: 'Düzəlt' }).first().click();
    await page.getByRole('dialog').waitFor();
    await page.getByLabel('İş saatlarını göstər').check();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${out}/${prefix}-09-branch-dialog.png` });
    await page.keyboard.press('Escape');
  } finally {
    await browser.close();
  }
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
