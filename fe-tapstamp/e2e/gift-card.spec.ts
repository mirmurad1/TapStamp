import { expect, test } from '@playwright/test';

import { phoneContext, putStampPolicy, registerFromTap, simulatorTap, staffLogin } from './support';

/**
 * Mərhələ 6 axını: müştəri kartı doldurur (qaydadakı möhür sayı) → bayram ekranı → hədiyyə kartının kodu → işçi kodu yoxlayır və
 * təhvil verir → müştərinin ekranında təbrik görünür; eyni kod ikinci dəfə "artıq istifadə olunub".
 * Sahib vaxt limitini müvəqqəti söndürür (9 möhür ard-arda) və sonda əvvəlki qaydanı bərpa edir.
 */
test('hədiyyə kartı: kart dolur, işçi kodu yoxlayıb təhvil verir, müştəri təbriki görür', async ({ browser }) => {
  test.setTimeout(180_000);
  const ownerContext = await browser.newContext();
  const owner = await ownerContext.newPage();
  await staffLogin(owner, 'owner@mockcoffee.az');
  const original = await putStampPolicy(owner, {
    minIntervalMinutes: 0,
    maxStampsPerDay: 50,
    requireStaffApproval: false,
    pendingTimeoutMinutes: 30,
  });

  try {
    const staffContext = await browser.newContext();
    const staff = await staffContext.newPage();
    await staffLogin(staff, 'barista@mockcoffee.az');

    const phone = await (await phoneContext(browser)).newPage();
    await registerFromTap(phone, await simulatorTap(staff, '04A1B2C3D4E5F1'), 'Hədiyyə Testi');
    await expect(phone.getByRole('heading', { name: 'Möhür əlavə olundu!' })).toBeVisible();
    // Hədiyyə üçün möhür sayı şirkətin cari qaydasından (sahib admin paneldə dəyişə bilər)
    const company = (await (await phone.request.get('/api/v1/public/company')).json()) as {
      rewardRule?: { stampsRequired?: number; rewardName?: { az?: string } };
    };
    const required = company.rewardRule?.stampsRequired ?? 9;
    for (let stamp = 2; stamp <= required; stamp++) {
      await phone.goto(await simulatorTap(staff, '04A1B2C3D4E5F1'));
      await expect(phone.getByRole('heading', { name: 'Möhür əlavə olundu!', includeHidden: true })).toBeAttached();
    }
    await expect(phone.getByRole('heading', { name: /Təbriklər!/ })).toBeVisible();
    await phone.getByRole('link', { name: 'Hədiyyə kartına bax' }).first().click();
    await phone.getByRole('button', { name: 'Kodu göstər' }).click();
    const code = (await phone.getByTestId('gift-card-code').textContent())?.trim() ?? '';
    expect(code).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);

    // İşçi (interfeysi rus dilində) kodu yoxlayır və təhvil verir
    await staff.goto('/admin/staff-console');
    await staff.locator('#gift-card-code').fill(code.replace('-', '').toLowerCase());
    await staff.locator('form:has(#gift-card-code) button[type="submit"]').click();
    const result = staff.getByTestId('gift-card-result');
    await expect(result).toContainText(company.rewardRule?.rewardName?.az ?? '');
    await result.locator('button').first().click();
    await expect(result.locator('svg.lucide-circle-check-big, svg.lucide-check-circle-2, svg.lucide-circle-check')).toBeVisible();

    // Müştərinin açıq ekranı statusu yoxlayır və təbrik göstərir
    await expect(phone.getByRole('heading', { name: 'Hədiyyəniz təhvil verildi! 🎉' })).toBeVisible({ timeout: 10_000 });

    // Eyni kod yenidən — artıq istifadə olunub
    await staff.locator('#gift-card-code').fill(code);
    await staff.locator('form:has(#gift-card-code) button[type="submit"]').click();
    await expect(result).toHaveClass(/border-destructive/);
  } finally {
    await putStampPolicy(owner, original);
    await ownerContext.close();
  }
});
