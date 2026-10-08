import { expect, test } from '@playwright/test';

import { customerSignUp } from './support';

/** Hüquqi sənədlər: istifadə şərtlərində şirkətin cari qaydaları görünür; "Geri" Parametrlərə qaytarır. */
test('istifadə şərtləri şirkətin parametrlərini göstərir və Parametrlərə qayıdır', async ({ page }) => {
  await customerSignUp(page, 'Hüquqi Test');
  const company = (await (await page.request.get('/api/v1/public/company')).json()) as {
    stampRules: { maxStampsPerDay: number };
    rewardRule: { stampsRequired: number };
  };

  await page.goto('/settings');
  await page.getByRole('link', { name: 'İstifadə şərtləri' }).click();
  await expect(page.getByRole('heading', { name: 'İstifadə şərtləri' })).toBeVisible();
  await expect(page.getByText(`Gündə ən çox ${company.stampRules.maxStampsPerDay} möhür verilir.`)).toBeVisible();
  await expect(page.getByText(`${company.rewardRule.stampsRequired} möhür toplayanda hədiyyə verilir`)).toBeVisible();
  await expect(page.getByText('{{')).toHaveCount(0);

  await page.getByRole('button', { name: 'Geri' }).click();
  await expect(page).toHaveURL(/\/settings$/);
});
