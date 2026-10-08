// Möhür ekranının damğa animasiyası: 3 kadr (enmə, vurulma, son vəziyyət). Dev mühiti və Edge lazımdır.
// Sahib vaxt limitini müvəqqəti söndürür və sonda bərpa edir; test müştərisi yaradılır (yalnız dev).
// İstifadə: node scripts/screenshots/stamp-animation.cjs [qovluq] [prefiks]
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'fe-tapstamp/node_modules/@playwright/test'));
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase7';
const base = 'https://mockcoffee.localhost';
const PASSWORD = 'Kofe-Dev-Parol-2026';

function totp(step) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = crypto.createHmac('sha1', Buffer.from('tapstamp-dev-totpkey', 'ascii')).update(counter).digest();
  const o = hmac[19] & 0xf;
  return String((((hmac[o] & 0x7f) << 24) | (hmac[o + 1] << 16) | (hmac[o + 2] << 8) | hmac[o + 3]) % 1e6).padStart(6, '0');
}

async function login(page, email) {
  await page.goto(`${base}/admin/login`);
  await page.getByLabel('Email').fill(email);
  await page.locator('input[type="password"]').fill(PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/admin(\/mfa)?\/?$/);
  if (page.url().endsWith('/mfa')) {
    await page.locator('input[autocomplete="one-time-code"]').fill(totp(Math.floor(Date.now() / 30000) + 1));
    await page.locator('form button[type="submit"]').click();
    await page.waitForURL(/\/admin\/?$/);
  }
}

async function policy(page, body) {
  return page.evaluate(async (next) => {
    const before = await (await fetch('/api/v1/admin/settings/stamp-policy')).json();
    const csrf = document.cookie.split('; ').find((c) => c.startsWith('__Host-XSRF-TOKEN='))?.slice(18) ?? '';
    const response = await fetch('/api/v1/admin/settings/stamp-policy', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrf) },
      body: JSON.stringify(next),
    });
    if (!response.ok) throw new Error(`policy ${response.status}`);
    return before;
  }, body);
}

async function tap(staff) {
  if (!staff.url().endsWith('/admin/dev/simulator')) await staff.goto(`${base}/admin/dev/simulator`);
  const select = staff.locator('#simulator-tag');
  await select.selectOption(await select.locator('option', { hasText: '04A1B2C3D4E5F1' }).getAttribute('value'));
  const link = staff.getByTestId('simulator-url');
  const previous = (await link.count()) ? await link.textContent() : null;
  await staff.locator('main button:has(svg.lucide-nfc)').click();
  if (previous) await staff.waitForFunction((p) => document.querySelector('[data-testid="simulator-url"]')?.textContent !== p, previous);
  return (await link.textContent()).trim();
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const options = { ignoreHTTPSErrors: true, locale: 'az-AZ' };
  const owner = await (await browser.newContext(options)).newPage();
  await login(owner, 'owner@mockcoffee.az');
  const original = await policy(owner, { minIntervalMinutes: 0, maxStampsPerDay: 50, requireStaffApproval: false, pendingTimeoutMinutes: 30 });
  try {
    const staff = await (await browser.newContext(options)).newPage();
    await login(staff, 'barista@mockcoffee.az');
    const phoneContext = await browser.newContext({ ...options, viewport: { width: 390, height: 844 }, isMobile: true });
    const phone = await phoneContext.newPage();
    await phone.goto(await tap(staff));
    await phone.getByRole('link', { name: 'Daxil ol və möhürü al' }).click();
    const number = `055${String(Math.floor(Math.random() * 1e7)).padStart(7, '0')}`;
    await phone.getByLabel('Telefon nömrəsi').fill(number);
    await phone.getByRole('button', { name: 'Kod göndər' }).click();
    await phone.getByLabel('Təsdiq kodu').waitFor();
    let code;
    for (let i = 0; i < 20 && !code; i++) {
      const entries = await (await phoneContext.request.get(`${base}/api/v1/dev/outbox?channel=SMS`)).json();
      code = entries.find((e) => e.to === `+994${number.slice(1)}`)?.body.match(/\b(\d{6})\b/)?.[1];
      if (!code) await new Promise((r) => setTimeout(r, 500));
    }
    await phone.getByLabel('Təsdiq kodu').fill(code);
    await phone.getByRole('button', { name: 'Təsdiqlə' }).click();
    await phone.getByLabel('Adınız').fill('Aysel');
    await phone.getByRole('checkbox').check();
    await phone.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();
    await phone.getByRole('heading', { name: 'Möhür əlavə olundu!' }).waitFor({ state: 'attached' });

    // İkinci möhür — animasiyanın kadrları
    await phone.goto(await tap(staff));
    await phone.locator('main canvas').waitFor({ state: 'attached' });
    await phone.waitForTimeout(450);
    await phone.screenshot({ path: `${out}/${prefix}-12-stamp-animation-1.png` });
    await phone.waitForTimeout(750);
    await phone.screenshot({ path: `${out}/${prefix}-13-stamp-animation-2.png` });
    await phone.waitForTimeout(2300);
    await phone.screenshot({ path: `${out}/${prefix}-14-stamp-animation-3.png` });
  } finally {
    await policy(owner, original);
    await browser.close();
  }
  console.log('done');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
