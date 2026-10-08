import { randomBytes } from 'node:crypto';

import { expect, test, type APIRequestContext } from '@playwright/test';

import { DEV_PASSWORD, customerSignUp, staffLogin } from './support';

/** Admin axınları UI ilə: işçi dəvəti → qəbul → giriş → silinmə; müştəriyə əl ilə möhür; NFC çipinin qeydiyyatı. */
async function invitationLink(request: APIRequestContext, email: string): Promise<string> {
  for (let attempt = 0; attempt < 20; attempt++) {
    const entries = (await (await request.get('/api/v1/dev/outbox?channel=EMAIL')).json()) as { to: string; body: string }[];
    const link = entries.find((entry) => entry.to === email)?.body.match(/https:\/\/\S+\/admin\/invitation#token=[\w-]+/)?.[0];
    if (link) {
      return link;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('Invitation email not found');
}

test('işçi dəvəti, qəbulu, girişi və silinməsi', async ({ browser }) => {
  const owner = await (await browser.newContext()).newPage();
  await staffLogin(owner, 'owner@mockcoffee.az');
  await owner.goto('/admin/team');
  const suffix = randomBytes(4).toString('hex');
  const email = `e2e-${suffix}@example.test`;
  const staffName = `E2E İşçi ${suffix}`;

  await owner.getByRole('button', { name: 'İşçi dəvət et' }).click();
  await owner.locator('#invite-email').fill(email);
  await owner.getByRole('button', { name: 'Dəvət göndər' }).click();
  await expect(owner.getByText(`Dəvət ${email} ünvanına göndərildi.`)).toBeVisible();

  const invited = await (await browser.newContext()).newPage();
  await invited.goto(await invitationLink(owner.request, email));
  await invited.getByLabel('Adınız və soyadınız').fill(staffName);
  await invited.locator('#new-password').fill(DEV_PASSWORD);
  await invited.locator('#confirm-password').fill(DEV_PASSWORD);
  await invited.getByRole('button', { name: 'Hesabı yarat' }).click();
  await expect(invited.getByRole('navigation').getByRole('link', { name: 'İşçi konsolu' })).toBeVisible();

  // Sahib işçini silir — sessiyası dərhal bağlanır, siyahıda görünmür
  await owner.reload();
  const row = owner.getByRole('row', { name: new RegExp(staffName) });
  await row.getByRole('button', { name: 'Sil' }).click();
  await owner.getByRole('dialog').getByRole('button', { name: 'Davam et' }).click();
  await expect(owner.getByText('İşçi silindi.')).toBeVisible();
  await expect(owner.getByRole('row', { name: new RegExp(staffName) })).toHaveCount(0);
  expect((await invited.request.get('/api/v1/admin/me')).status()).toBe(401);
});

test('sahib müştəriyə əl ilə möhür əlavə edir', async ({ browser }) => {
  const customer = await (await browser.newContext()).newPage();
  const name = `Düzəliş ${randomBytes(3).toString('hex')}`;
  await customerSignUp(customer, name);

  const owner = await (await browser.newContext()).newPage();
  await staffLogin(owner, 'owner@mockcoffee.az');
  await owner.goto('/admin/customers');
  await owner.getByRole('searchbox').fill(name);
  await owner.getByRole('searchbox').press('Enter');
  await owner.getByRole('link', { name }).click();

  await owner.getByRole('button', { name: 'Möhürü düzəlt' }).click();
  const dialog = owner.getByRole('dialog');
  await dialog.getByLabel('Say (1–50)').fill('2');
  await dialog.getByLabel('Səbəb').fill('Çip işləmədi, kassada vuruldu');
  await dialog.getByRole('button', { name: 'Yadda saxla' }).click();
  await expect(dialog).toBeHidden();
  await expect(owner.getByText('Möhür (əl ilə)').first()).toBeVisible();

  const me = (await (await customer.request.get('/api/v1/me')).json()) as { card: { currentStamps: number } };
  expect(me.card.currentStamps).toBe(2);
});

test('platforma admini şirkətə NFC çipi əlavə edir və deaktiv edir; sahib yalnız baxır', async ({ browser }) => {
  const platformUrl = new URL(test.info().project.use.baseURL ?? '').origin.replace('mockcoffee.', 'platform.');
  const admin = await (await browser.newContext({ baseURL: platformUrl })).newPage();
  await staffLogin(admin, 'superadmin@platform.localhost');
  await admin.locator('tr', { hasText: 'mockcoffee.localhost' }).getByRole('link').click();
  const uid = `04${randomBytes(6).toString('hex').toUpperCase()}`;
  const label = `E2E ${uid.slice(-4)}`;

  await admin.getByRole('button', { name: 'Çip əlavə et' }).click();
  await admin.getByLabel('UID').fill(uid);
  await admin.getByLabel('Etiket').fill(label);
  await admin.getByRole('button', { name: 'Qeydiyyatdan keçir' }).click();

  // Kart platforma kartının içindədir: ən daxili uyğun element çipin öz kartıdır
  const card = admin.locator('div.rounded-xl', { hasText: uid }).last();
  await expect(card.getByText('Yeni açar v1 — paket yüklənməyib')).toBeVisible();
  await expect(card.getByText(/^(Pulsuz|Pullu)$/)).toBeVisible();
  await card.getByRole('button', { name: 'Deaktiv et' }).click();
  await admin.getByRole('dialog').getByRole('button', { name: 'Davam et' }).click();
  await expect(card.getByText('Deaktiv', { exact: true })).toBeVisible();

  const owner = await (await browser.newContext()).newPage();
  await staffLogin(owner, 'owner@mockcoffee.az');
  await owner.goto('/admin/tags');
  await expect(owner.locator('div.rounded-xl', { hasText: uid }).first()).toBeVisible();
  await expect(owner.getByRole('button', { name: 'Çip əlavə et' })).toHaveCount(0);
  await expect(owner.getByRole('button', { name: 'Deaktiv et' })).toHaveCount(0);
});
