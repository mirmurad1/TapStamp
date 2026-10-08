// Mərhələ 8: telefon ölçüsü, rus dili və admin paneli. Dev mühiti və Edge lazımdır; test müştərisi yaradılır.
// İstifadə: node scripts/screenshots/phone-and-languages.cjs [qovluq] [prefiks]
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'fe-tapstamp/node_modules/@playwright/test'));
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase8';
const base = 'https://mockcoffee.localhost';

function totp(step) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = crypto.createHmac('sha1', Buffer.from('tapstamp-dev-totpkey', 'ascii')).update(counter).digest();
  const o = hmac[19] & 0xf;
  return String((((hmac[o] & 0x7f) << 24) | (hmac[o + 1] << 16) | (hmac[o + 2] << 8) | hmac[o + 3]) % 1e6).padStart(6, '0');
}

async function signUp(page, request) {
  await page.goto(`${base}/login`);
  const number = `055${String(Math.floor(Math.random() * 1e7)).padStart(7, '0')}`;
  await page.getByLabel('Telefon nömrəsi').fill(number);
  await page.getByRole('button', { name: 'Kod göndər' }).click();
  await page.getByLabel('Təsdiq kodu').waitFor();
  let code;
  for (let i = 0; i < 20 && !code; i++) {
    const entries = await (await request.get(`${base}/api/v1/dev/outbox?channel=SMS`)).json();
    code = entries.find((e) => e.to === `+994${number.slice(1)}`)?.body.match(/\b(\d{6})\b/)?.[1];
    if (!code) await new Promise((r) => setTimeout(r, 500));
  }
  await page.getByLabel('Təsdiq kodu').fill(code);
  await page.getByRole('button', { name: 'Təsdiqlə' }).click();
  await page.getByLabel('Adınız').fill('Aysel');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const phone = { ignoreHTTPSErrors: true, locale: 'az-AZ', viewport: { width: 390, height: 844 }, isMobile: true };
  try {
    // Müştəri (telefon)
    const customerContext = await browser.newContext({ ...phone });
    const customer = await customerContext.newPage();
    await signUp(customer, customerContext.request);
    await customer.goto(`${base}/`);
    await customer.waitForTimeout(800);
    await customer.screenshot({ path: `${out}/${prefix}-03-customer-home.png`, fullPage: true });
    await customer.goto(`${base}/settings`);
    await customer.waitForTimeout(600);
    await customer.screenshot({ path: `${out}/${prefix}-04-customer-settings.png`, fullPage: true });

    // Giriş səhifəsi rus dilində
    const ru = await (await browser.newContext(phone)).newPage();
    await ru.goto(`${base}/login`);
    await ru.getByRole('radio', { name: 'RU' }).first().click();
    await ru.waitForTimeout(400);
    await ru.screenshot({ path: `${out}/${prefix}-05-login-ru.png` });

    // Admin: masaüstü və telefon
    const adminContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'az-AZ', viewport: { width: 1280, height: 900 } });
    const admin = await adminContext.newPage();
    await admin.goto(`${base}/admin/login`);
    await admin.getByLabel('Email').fill('owner@mockcoffee.az');
    await admin.locator('input[type="password"]').fill('Kofe-Dev-Parol-2026');
    await admin.locator('form button[type="submit"]').click();
    await admin.waitForURL(/\/admin(\/mfa)?\/?$/);
    if (admin.url().endsWith('/mfa')) {
      await admin.locator('input[autocomplete="one-time-code"]').fill(totp(Math.floor(Date.now() / 30000) + 1));
      await admin.locator('form button[type="submit"]').click();
      await admin.waitForURL(/\/admin\/?$/);
    }
    await admin.getByText('Ən sıx saatlar').waitFor();
    await admin.waitForTimeout(800);
    await admin.screenshot({ path: `${out}/${prefix}-06-dashboard.png`, fullPage: true });
    await admin.setViewportSize({ width: 375, height: 812 });
    await admin.reload();
    await admin.getByText('Ən sıx saatlar').waitFor();
    await admin.waitForTimeout(800);
    await admin.screenshot({ path: `${out}/${prefix}-07-dashboard-phone.png`, fullPage: true });
  } finally {
    await browser.close();
  }
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
