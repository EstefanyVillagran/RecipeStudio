/* Service Worker de RecipeStudio.
 * - Precarga el "cascarón" de la app para que abra sin conexión.
 * - Navegación: red primero y, si no hay internet, la última versión guardada (o offline.html).
 * - API (/estado, /sesion, /evento...): NUNCA se guarda en caché (son datos en vivo); sin red responde 503 en JSON.
 * - Íconos, manifiesto y la librería del QR: se sirven de caché y se actualizan en segundo plano.
 */
const VERSION = 'recipestudio-v1';
const CASCARON = ['/', '/offline.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png'];
const API = /^\/(estado|sesion|conectar|chat|reportes|mis_reportes|reportar|reset|login|logout|evento|api|admin|desvincular)/;

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(CASCARON)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((claves) => Promise.all(claves.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.origin === self.location.origin && API.test(url.pathname)) {
    e.respondWith(
      fetch(req).catch(() => new Response(JSON.stringify({ offline: true, error: 'Sin conexión con la estación' }), {
        status: 503, headers: { 'Content-Type': 'application/json' }
      }))
    );
    return;
  }

  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req)
        .then((r) => { const copia = r.clone(); caches.open(VERSION).then((c) => c.put('/', copia)); return r; })
        .catch(() => caches.match('/').then((r) => r || caches.match('/offline.html')))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then((guardado) => {
      const red = fetch(req).then((r) => {
        if (r && (r.ok || r.type === 'opaque')) { const copia = r.clone(); caches.open(VERSION).then((c) => c.put(req, copia)); }
        return r;
      }).catch(() => guardado);
      return guardado || red;
    })
  );
});
