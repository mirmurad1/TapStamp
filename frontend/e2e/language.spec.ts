import { expect, test } from '@playwright/test';

import { customerSignUp, staffLogin } from './support';

/** Dil axını: qonaq dili dəyişir; daxil olmuş müştərinin və işçinin dili profildə saxlanılır (bütün cihazlarda). */
test('qonaq giriş səhifəsində dili dəyişir', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Kod göndər' })).toBeVisible();

  await page.getByRole('radio', { name: 'EN' }).first().click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.getByRole('button', { name: 'Send code' })).toBeVisible();

  await page.getByRole('radio', { name: 'RU' }).first().click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
  await expect(page.getByRole('button', { name: 'Получить код' })).toBeVisible();

  // Seçim bu cihazda yadda qalır
  await page.reload();
  await expect(page.getByRole('button', { name: 'Получить код' })).toBeVisible();
});

test('müştərinin dili profildə saxlanılır', async ({ page }) => {
  await customerSignUp(page, 'Dil Testi');
  await page.goto('/settings');
  await page.getByRole('radio', { name: 'RU' }).first().click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
  await expect
    .poll(async () => ((await (await page.request.get('/api/v1/me')).json()) as { preferredLanguage: string }).preferredLanguage)
    .toBe('ru');

  // Başqa "cihazda" yerli seçim yoxdur — dil profildən gəlir
  await page.evaluate(() => window.localStorage.clear());
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
});

test('işçinin dili profildə saxlanılır və yeni girişdə tətbiq olunur', async ({ browser }) => {
  const first = await (await browser.newContext()).newPage();
  await staffLogin(first, 'manager@mockcoffee.az');
  try {
    await first.getByRole('radio', { name: 'EN' }).first().click();
    await expect(first.locator('html')).toHaveAttribute('lang', 'en');
    await expect(first.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    // Başqa cihazdan giriş — eyni dil
    const second = await (await browser.newContext({ locale: 'az-AZ' })).newPage();
    await staffLogin(second, 'manager@mockcoffee.az');
    await expect(second.locator('html')).toHaveAttribute('lang', 'en');
    await expect(second.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  } finally {
    await first.getByRole('radio', { name: 'AZ' }).first().click();
    await expect(first.locator('html')).toHaveAttribute('lang', 'az');
  }
});
