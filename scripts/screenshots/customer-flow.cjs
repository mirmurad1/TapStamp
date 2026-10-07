// Vizual yoxlama: müştəri axınının ekran görüntüləri (işləyən dev mühiti, sistemdəki Edge).
// Diqqət: test müştərisi yaradır və kartını dev bazasında doldurur (yalnız dev).
const path = require('path');
const { execSync } = require('child_process');
const root = path.resolve(__dirname, '../..');
const { chromium } = require(path.join(root, 'frontend/node_modules/@playwright/test'));
// İstifadə: node scripts/screenshots/<skript>.cjs [qovluq] [prefiks] — standart: docs/screenshots, prefiks 'phase5'
const out = process.argv[2] ?? path.join(root, 'docs/screenshots');
const prefix = process.argv[3] ?? 'phase5';
const docker = `"${process.env.LOCALAPPDATA}/Programs/DockerDesktop/resources/bin/docker"`;
const base = 'https://mockcoffee.localhost';

function sql(statement) {
  return execSync(`${docker} compose exec -T postgres psql -U postgres -d loyalty_t_mockcoffee -Atc "${statement}"`, {
    cwd: root,
  }).toString().trim();
}

async function simulatorTap(staff) {
  await staff.goto(`${base}/admin/dev/simulator`);
  const select = staff.locator('#simulator-tag');
  await select.selectOption((await select.locator('option', { hasText: '04A1B2C3D4E5F2' }).getAttribute('value')) ?? '');
  await staff.locator('main button:has(svg.lucide-nfc)').click();
  return (await staff.getByTestId('simulator-url').textContent()).trim();
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const staffContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 800 } });
  const staff = await staffContext.newPage();
  await staff.goto(`${base}/admin/login`);
  await staff.getByLabel('Email').fill('barista@mockcoffee.az');
  await staff.locator('input[type="password"]').fill('Kofe-Dev-Parol-2026');
  await staff.locator('form button[type="submit"]').click();
  await staff.waitForURL(/\/admin\/?$/);
  const url1 = await simulatorTap(staff);
  await staff.screenshot({ path: `${out}/${prefix}-01-admin-nfc-simulator.png` });

  const phone = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 390, height: 844 }, isMobile: true, locale: 'az-AZ' });
  const page = await phone.newPage();
  await page.goto(url1);
  await page.getByRole('link', { name: 'Daxil ol və möhürü al' }).waitFor();
  await page.screenshot({ path: `${out}/${prefix}-02-tap-guest.png` });
  await page.getByRole('link', { name: 'Daxil ol və möhürü al' }).click();
  const number = `055${String(Math.floor(Math.random() * 1e7)).padStart(7, '0')}`;
  await page.getByLabel('Telefon nömrəsi').fill(number);
  await page.getByRole('button', { name: 'Kod göndər' }).click();
  await page.getByLabel('Təsdiq kodu').waitFor();
  let code;
  for (let i = 0; i < 20 && !code; i++) {
    const entries = await (await phone.request.get(`${base}/api/v1/dev/outbox?channel=SMS`)).json();
    code = entries.find((e) => e.to === `+994${number.slice(1)}`)?.body.match(/\b(\d{6})\b/)?.[1];
    if (!code) await new Promise((r) => setTimeout(r, 500));
  }
  await page.getByLabel('Təsdiq kodu').fill(code);
  await page.getByRole('button', { name: 'Təsdiqlə' }).click();
  const name = `Vizual ${Math.floor(Math.random() * 1000)}`;
  await page.getByLabel('Adınız').fill(name);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();
  await page.getByRole('heading', { name: 'Möhür əlavə olundu!' }).waitFor();
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${out}/${prefix}-03-stamp-added-first-login.png`, fullPage: true });

  // Kartı demək olar doldururuq və cooldown-u keçirik (yalnız dev bazası)
  sql(`UPDATE loyalty_accounts SET current_stamps = 8 WHERE customer_id = (SELECT id FROM customers WHERE name = '${name}')`);
  sql(`UPDATE stamps SET created_at = created_at - interval '2 hours', local_date = local_date - 1 WHERE customer_id = (SELECT id FROM customers WHERE name = '${name}')`);
  const url2 = await simulatorTap(staff);
  await page.goto(url2);
  await page.getByRole('heading', { name: /Təbriklər/ }).waitFor();
  await page.waitForTimeout(900);
  await page.screenshot({ path: `${out}/${prefix}-04-card-complete-celebration.png` });
  await page.getByRole('link', { name: 'Hədiyyə kartına bax' }).first().click();
  await page.getByRole('button', { name: 'Kodu göstər' }).click();
  await page.getByTestId('gift-card-code').waitFor();
  await page.screenshot({ path: `${out}/${prefix}-05-gift-card-code-qr.png` });
  await page.keyboard.press('Escape');
  await page.goto(`${base}/`);
  await page.getByRole('heading', { name: `Salam, ${name}!` }).waitFor();
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${out}/${prefix}-06-home-returning.png`, fullPage: true });

  const tea = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 390, height: 844 }, isMobile: true, locale: 'az-AZ' });
  const teaPage = await tea.newPage();
  await teaPage.goto('https://demo-cay.localhost/');
  await teaPage.getByRole('link', { name: 'Daxil ol və möhür topla' }).waitFor();
  await teaPage.screenshot({ path: `${out}/${prefix}-07-other-tenant-guest.png`, fullPage: true });
  await browser.close();
  console.log('done', name);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
