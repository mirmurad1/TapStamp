import { defineConfig } from '@playwright/test';

/**
 * Uçdan-uca (E2E) testlər işləyən dev mühitinə qarşı: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`.
 * Brauzer yüklənmir — sistemdəki Microsoft Edge (Windows) və ya Chrome işlədilir: E2E_BROWSER_CHANNEL=chrome.
 * Dev sertifikatı öz-özünə imzalıdır, ona görə HTTPS xətaları nəzərə alınmır (yalnız lokal).
 */
const httpsPort = process.env.HTTPS_PORT ?? '443';
const portSuffix = httpsPort === '443' ? '' : `:${httpsPort}`;

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  // Bir işçi: dev hesablarının TOTP kodu eyni 30 saniyəlik addımda iki dəfə qəbul olunmur (paralel girişlər toqquşur)
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `https://mockcoffee.localhost${portSuffix}`,
    channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge',
    ignoreHTTPSErrors: true,
    locale: 'az-AZ',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
});
