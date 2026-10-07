// Platforma panelinin səhifələri (platforma admini) — Mərhələ 7 ekran görüntüləri. Heç nə dəyişdirilmir.
// İstifadə: node scripts/screenshots/platform-pages.cjs [qovluq] [prefiks]
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'frontend/node_modules/@playwright/test'));
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase7';
const base = 'https://platform.localhost';

// [fayl adı, yol, gözlənilən mətn, tam səhifə]
const PAGES = [
  ['11-platform-audit', '/admin/platform-audit', 'Platforma audit jurnalı', false],
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
    await page.getByLabel('Email').fill('superadmin@platform.localhost');
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
    // Şirkətin səhifəsi: göstəricilər, sahiblər, domenlər
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(base + '/admin');
    await page.locator('main a[href*="/tenants/"]:not([href$="/new"])').first().click();
    await page.getByText('Domenlər').first().waitFor();
    await page.waitForTimeout(600);
    await page.screenshot({ path: `${out}/${prefix}-10-platform-tenant-domains.png`, fullPage: true });
  } finally {
    await browser.close();
  }
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
