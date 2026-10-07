/*
 * Müştəri tətbiqinin service worker-i (PWA).
 *
 * Qaydalar:
 * - /assets/* (hash-li, dəyişməz fayllar) — əvvəlcə keşdən, yoxdursa şəbəkədən (cache-first).
 * - Səhifə keçidləri (HTML) — həmişə şəbəkədən; internet yoxdursa oflayn ekran (möhür və kart internetsiz işləmir).
 * - API (/api/), NFC toxunuşu (/t/), manifest və admin paneli (/admin) HEÇ VAXT keşlənmir: möhür, giriş və hədiyyə
 *   kartı həmişə serverdən gəlir.
 */
const VERSION = 'cl-v2';
const SHELL = `${VERSION}-shell`;
const ASSETS = `${VERSION}-assets`;
const OFFLINE_URLS = ['/offline.html', '/offline.css', '/favicon.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL)
      .then((cache) => cache.addAll(OFFLINE_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => !key.startsWith(`${VERSION}-`)).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

function neverCache(url) {
  return (
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/t/') ||
    url.pathname.startsWith('/admin') ||
    url.pathname === '/manifest.webmanifest' ||
    url.pathname === '/sw.js'
  );
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') {
    return;
  }
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || neverCache(url)) {
    return;
  }

  // Oflayn ekranın öz faylları (stil, ikon) — keşdən
  if (OFFLINE_URLS.includes(url.pathname)) {
    event.respondWith(caches.match(request).then((cached) => cached || fetch(request)));
    return;
  }

  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.open(ASSETS).then((cache) =>
        cache.match(request).then(
          (cached) =>
            cached ||
            fetch(request).then((response) => {
              if (response.ok) {
                cache.put(request, response.clone());
              }
              return response;
            }),
        ),
      ),
    );
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.open(SHELL).then((cache) => cache.match('/offline.html'))));
  }
});
