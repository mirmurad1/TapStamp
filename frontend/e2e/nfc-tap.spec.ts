import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/**
 * İlk E2E axını (Mərhələ 5): işçi simulyatorda real NTAG 424 kriptoqrafiyası ilə imzalı toxunuş linki yaradır →
 * yeni müştəri linki "telefonda" açır → SMS kodu ilə qeydiyyatdan keçir → möhür avtomatik verilir → eyni linkin
 * təkrar açılması replay kimi rədd edilir.
 */

const STAFF_EMAIL = 'barista@mockcoffee.az';
const STAFF_PASSWORD = process.env.DEV_SEED_ADMIN_PASSWORD ?? 'Kofe-Dev-Parol-2026';

function uniquePhone(): string {
  return `055${String(Math.floor(Math.random() * 10_000_000)).padStart(7, '0')}`;
}

async function signedTapUrl(page: Page): Promise<string> {
  await page.goto('/admin/login');
  await page.getByLabel('Email').fill(STAFF_EMAIL);
  await page.locator('input[type="password"]').fill(STAFF_PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/admin\/?$/);

  await page.goto('/admin/dev/simulator');
  // Real NTAG 424 DNA kriptoqrafiyası ilə emulyasiya olunan çip
  const select = page.locator('#simulator-tag');
  const value = await select.locator('option', { hasText: '04A1B2C3D4E5F2' }).getAttribute('value');
  await select.selectOption(value ?? '');
  await page.locator('main button:has(svg.lucide-nfc)').click();
  const url = (await page.getByTestId('simulator-url').textContent())?.trim() ?? '';
  expect(url).toMatch(/\/t\/04A1B2C3D4E5F2\?picc=[0-9A-F]{32}&cmac=[0-9A-F]{16}$/);
  return url;
}

async function latestSmsCode(request: APIRequestContext, phone: string): Promise<string> {
  const e164 = `+994${phone.slice(1)}`;
  for (let attempt = 0; attempt < 20; attempt++) {
    const response = await request.get('/api/v1/dev/outbox?channel=SMS');
    const entries = (await response.json()) as { to: string; body: string }[];
    const match = entries.find((entry) => entry.to === e164)?.body.match(/\b(\d{6})\b/);
    if (match?.[1]) {
      return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('SMS code not found in dev outbox');
}

test('NFC toxunuşu: qonaq qeydiyyatdan keçir, möhür alır, link təkrar işləmir', async ({ browser }) => {
  const staff = await browser.newPage();
  const tapUrl = await signedTapUrl(staff);
  await staff.close();

  // Müştərinin telefonu: ayrı brauzer konteksti (öz cookie-ləri)
  const phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true });
  const phone = await phoneContext.newPage();
  await phone.goto(tapUrl);
  await expect(phone).toHaveURL(/\/tap\/[A-Za-z0-9_-]{43}$/);
  await phone.getByRole('link', { name: 'Daxil ol və möhürü al' }).click();

  const number = uniquePhone();
  await phone.getByLabel('Telefon nömrəsi').fill(number);
  await phone.getByRole('button', { name: 'Kod göndər' }).click();
  await phone.getByLabel('Təsdiq kodu').fill(await latestSmsCode(phoneContext.request, number));
  await phone.getByRole('button', { name: 'Təsdiqlə' }).click();

  await phone.getByLabel('Adınız').fill('E2E Müştəri');
  await phone.getByRole('checkbox').check();
  await phone.getByRole('button', { name: 'Qeydiyyatı tamamla' }).click();

  // Girişdən sonra toxunuş səhifəsinə qayıdır və möhür avtomatik verilir
  await expect(phone.getByRole('heading', { name: 'Möhür əlavə olundu!' })).toBeVisible();
  // Animasiyalar bitəndən sonra kart ekranı avtomatik açılır
  await expect(phone).toHaveURL(/\/$/, { timeout: 10_000 });
  await expect(phone.getByRole('heading', { name: 'Salam, E2E Müştəri!' })).toBeVisible();

  // Eyni imzalı link ikinci dəfə — replay
  await phone.goto(tapUrl);
  await expect(phone).toHaveURL(/\/tap\/error\?code=TAP_REPLAYED$/);
  await expect(phone.getByText('Bu link artıq istifadə olunub. Hər toxunuş yalnız bir dəfə keçərlidir.')).toBeVisible();

  await phone.getByRole('link', { name: 'Kartıma bax' }).click();
  await expect(phone.getByRole('heading', { name: 'Salam, E2E Müştəri!' })).toBeVisible();
  await expect(phone.getByRole('img', { name: 'Möhür vurulub' })).toHaveCount(1);
  await phoneContext.close();
});
