// Hesab səhifəsində 2FA idarəsi (söndür/sil) və komandada işçinin silinməsi — ekran görüntüləri.
// İşləyən dev mühiti və sistemdəki Edge lazımdır. Heç nə dəyişdirilmir: dialoqlar açılır və ləğv edilir.
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'fe-tapstamp/node_modules/@playwright/test'));
// İstifadə: node scripts/screenshots/account-team.cjs [qovluq] [prefiks]
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase6';
const base = 'https://mockcoffee.localhost';

function totp(step) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = crypto.createHmac('sha1', Buffer.from('tapstamp-dev-totpkey', 'ascii')).update(counter).digest();
  const o = hmac[19] & 0xf;
  return String((((hmac[o] & 0x7f) << 24) | (hmac[o + 1] << 16) | (hmac[o + 2] << 8) | hmac[o + 3]) % 1e6).padStart(6, '0');
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const page = await (await browser.newContext({ ignoreHTTPSErrors: true, locale: 'az-AZ', viewport: { width: 1280, height: 900 } })).newPage();
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

    await page.goto(`${base}/admin/account`);
    await page.getByRole('heading', { name: 'İki addımlı yoxlama' }).waitFor();
    await page.screenshot({ path: `${out}/${prefix}-10-account-2fa.png`, fullPage: true });
    const remove = page.getByRole('button', { name: '2FA-nı sil' });
    if (await remove.count()) {
      await remove.click();
      await page.getByRole('dialog').waitFor();
      await page.waitForTimeout(300);
      await page.screenshot({ path: `${out}/${prefix}-11-account-2fa-remove-dialog.png` });
      await page.keyboard.press('Escape');
    }

    await page.goto(`${base}/admin/team`);
    const del = page.getByRole('button', { name: 'Sil' }).first();
    await del.waitFor();
    await page.screenshot({ path: `${out}/${prefix}-12-team-delete.png` });
    await del.click();
    await page.getByRole('dialog').waitFor();
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${out}/${prefix}-13-team-delete-confirm.png` });
    await page.getByRole('button', { name: 'Ləğv et' }).click();
  } finally {
    await browser.close();
  }
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
