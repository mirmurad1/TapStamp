// Sahibin parametrlər səhifəsinin görüntüsü (dev TOTP sirri ilə giriş)
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'frontend/node_modules/@playwright/test'));
// İstifadə: node scripts/screenshots/<skript>.cjs [qovluq] [prefiks] — standart: docs/screenshots, prefiks 'phase5'
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase5';

function totp(secret, offset = 0) {
  const counter = Math.floor(Date.now() / 30000) + offset;
  const buffer = Buffer.alloc(8);
  buffer.writeBigUInt64BE(BigInt(counter));
  const hmac = crypto.createHmac('sha1', secret).update(buffer).digest();
  const o = hmac[19] & 0xf;
  const code = ((hmac[o] & 0x7f) << 24) | (hmac[o + 1] << 16) | (hmac[o + 2] << 8) | hmac[o + 3];
  return String(code % 1_000_000).padStart(6, '0');
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 1000 }, locale: 'az-AZ' });
  const page = await context.newPage();
  await page.goto('https://mockcoffee.localhost/admin/login');
  await page.getByLabel('Email').fill('owner@mockcoffee.az');
  await page.locator('input[type="password"]').fill('Kofe-Dev-Parol-2026');
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/admin\/mfa/);
  await page.getByLabel('Təsdiq kodu').fill(totp(Buffer.from('cafe-loyalty-dev-key', 'ascii'), 1));
  await page.getByRole('button', { name: 'Təsdiqlə' }).click();
  await page.waitForURL(/\/admin\/?$/);
  await page.goto('https://mockcoffee.localhost/admin/settings');
  await page.getByLabel('Hədiyyə üçün lazım olan möhür sayı').waitFor();
  await page.screenshot({ path: `${out}/${prefix}-08-admin-settings.png`, fullPage: true });
  await browser.close();
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
