import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

import { customerSignUp, staffLogin } from './support';

/**
 * Keyfiyyət yoxlaması: hər əsas səhifə açıq və qaranlıq rejimdə, telefon (375 px) və masaüstü ölçüsündə
 * 1) üfüqi daşma yoxdur, 2) axe (WCAG 2.1 A/AA) ciddi və kritik pozuntu tapmır.
 */
const VIEWPORTS = [
  { name: 'phone', width: 375, height: 812 },
  { name: 'desktop', width: 1280, height: 900 },
] as const;
const SCHEMES = ['light', 'dark'] as const;

const CUSTOMER_PAGES = ['/', '/gift-cards', '/settings'];
const ADMIN_PAGES = ['/admin', '/admin/customers', '/admin/staff-console', '/admin/team', '/admin/tags', '/admin/settings', '/admin/audit', '/admin/account'];

async function audit(page: Page, label: string) {
  await page.waitForLoadState('networkidle');
  // Animasiyalar bitsin (konfeti, fade-up) — kontrast yarımşəffaf vəziyyətdə ölçülməsin
  await page.waitForTimeout(700);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, `${label}: üfüqi daşma`).toBeLessThanOrEqual(1);
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const serious = result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const report = serious.map((v) => `${v.id}: ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  expect(report, `${label}: axe`).toEqual([]);
}

for (const viewport of VIEWPORTS) {
  for (const scheme of SCHEMES) {
    test(`müştəri tətbiqi — ${viewport.name}, ${scheme}`, async ({ browser }) => {
      const context = await browser.newContext({ viewport, colorScheme: scheme, isMobile: viewport.name === 'phone' });
      const page = await context.newPage();
      await page.goto('/login');
      await audit(page, `/login ${viewport.name} ${scheme}`);
      await customerSignUp(page, 'Keyfiyyət Testi');
      for (const path of CUSTOMER_PAGES) {
        await page.goto(path);
        await audit(page, `${path} ${viewport.name} ${scheme}`);
      }
      await context.close();
    });

    test(`admin paneli — ${viewport.name}, ${scheme}`, async ({ browser }) => {
      const context = await browser.newContext({ viewport, colorScheme: scheme });
      const page = await context.newPage();
      await page.goto('/admin/login');
      await audit(page, `/admin/login ${viewport.name} ${scheme}`);
      await staffLogin(page, 'owner@mockcoffee.az');
      for (const path of ADMIN_PAGES) {
        await page.goto(path);
        await audit(page, `${path} ${viewport.name} ${scheme}`);
      }
      await context.close();
    });
  }
}

/** Brauzer sessiyası ilə PATCH (CSRF cookie-dən). */
async function patchJson(page: Page, url: string, body: Record<string, unknown>) {
  await page.evaluate(
    async ({ url: target, body: payload }) => {
      // Giriş zamanı CSRF cookie yenilənə bilər — 403 olarsa token təzədən oxunub bir dəfə təkrarlanır
      const send = () => {
        const csrf = document.cookie.split('; ').find((c) => c.startsWith('__Host-XSRF-TOKEN='))?.slice('__Host-XSRF-TOKEN='.length) ?? '';
        return fetch(target, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrf) },
          body: JSON.stringify(payload),
        });
      };
      let response = await send();
      if (response.status === 403) {
        await fetch('/api/v1/csrf');
        response = await send();
      }
      if (!response.ok) {
        throw new Error(`${target} ${response.status}`);
      }
    },
    { url, body },
  );
}

// İngilis və rus dilində mətnlər daha uzundur — daşma və kontrast bu dillərdə də yoxlanılır (telefon ölçüsü)
for (const lang of ['en', 'ru'] as const) {
  test(`dil: ${lang} — müştəri və admin, telefon`, async ({ browser }) => {
    const viewport = { width: 375, height: 812 };
    const customer = await (await browser.newContext({ viewport, isMobile: true })).newPage();
    await customerSignUp(customer, 'Language Test');
    await patchJson(customer, '/api/v1/me', { preferredLanguage: lang });
    for (const path of CUSTOMER_PAGES) {
      await customer.goto(path);
      await expect(customer.locator('html')).toHaveAttribute('lang', lang);
      await audit(customer, `${path} ${lang}`);
    }

    const admin = await (await browser.newContext({ viewport })).newPage();
    await staffLogin(admin, 'owner@mockcoffee.az');
    try {
      await patchJson(admin, '/api/v1/admin/me', { preferredLanguage: lang });
      for (const path of ADMIN_PAGES) {
        await admin.goto(path);
        await expect(admin.locator('html')).toHaveAttribute('lang', lang);
        await audit(admin, `${path} ${lang}`);
      }
    } finally {
      await patchJson(admin, '/api/v1/admin/me', { preferredLanguage: 'az' });
    }
  });
}
