/*
 * Müştəri tətbiqinin service worker-i (PWA).
 *
 * Qaydalar:
 * - /assets/* (hash-li, dəyişməz fayllar) — əvvəlcə keşdən, yoxdursa şəbəkədən (cache-first).
 * - Səhifə keçidləri (HTML) — həmişə şəbəkədən; internet yoxdursa oflayn ekran (möhür və kart internetsiz işləmir).
 * - API (/api/), NFC toxunuşu (/t/), manifest və admin paneli (/admin) HEÇ VAXT keşlənmir: möhür, giriş və hədiyyə
 *   kartı həmişə serverdən gəlir.
 * - Web push: server şifrəli mesaj göndərir (brauzer açır), burada bildiriş göstərilir; klikdə tətbiq lazımi
 *   səhifədə açılır (artıq açıqdırsa, o pəncərəyə keçilir).
 */
const VERSION = 'cl-v4';
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

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = {};
  }
  const title = typeof data.title === 'string' ? data.title : '';
  if (!title) {
    return;
  }
  event.waitUntil(
    self.registration.showNotification(title, {
      body: typeof data.body === 'string' ? data.body : '',
      icon: '/api/v1/public/icons/192.png',
      badge: '/api/v1/public/icons/192.png',
      tag: typeof data.tag === 'string' ? data.tag : undefined,
      data: { url: typeof data.url === 'string' && data.url.startsWith('/') ? data.url : '/' },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = new URL(event.notification.data?.url || '/', self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => client.url.startsWith(self.location.origin));
      if (open) {
        return open.navigate(target).then((client) => (client || open).focus());
      }
      return self.clients.openWindow(target);
    }),
  );
});
