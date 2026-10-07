/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

// Dev serverdə backend-ə proxy; Host başlığı saxlanılır ki, backend tenant-ı domenə görə tapa bilsin
const backendUrl = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';
const backendProxy = { target: backendUrl, changeOrigin: false, xfwd: true };

/**
 * Dev serverdə /admin/* dərin linklərini admin tətbiqinin index.html faylına yönləndirir.
 * Production-da eyni işi frontend konteynerindəki nginx görür.
 */
function adminSpaFallback(): Plugin {
  return {
    name: 'admin-spa-fallback',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const path = req.url?.split('?')[0] ?? '';
        if (/^\/admin(\/|$)/.test(path) && !/\.[a-z0-9]+$/i.test(path)) {
          req.url = '/admin/index.html';
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), adminSpaFallback()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true,
    // *.localhost dev domenləri (mockcoffee.localhost və s.)
    allowedHosts: ['.localhost'],
    proxy: {
      '/api': backendProxy,
      '/t/': backendProxy,
      '/manifest.webmanifest': backendProxy,
    },
  },
  build: {
    sourcemap: false,
    rollupOptions: {
      input: {
        customer: fileURLToPath(new URL('./index.html', import.meta.url)),
        admin: fileURLToPath(new URL('./admin/index.html', import.meta.url)),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // Tətbiq səviyyəli UI testləri (tam giriş axını) yüklü CI maşınında 5 saniyədən uzun çəkə bilər
    testTimeout: 20_000,
    css: false,
  },
});
