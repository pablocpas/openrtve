/*
 * Service worker de OpenRTVE. Guarda la propia app para que arranque sin red: el
 * catálogo ya vive en IndexedDB, las imágenes en la caché HTTP del navegador y el
 * vídeo nunca se guarda. Nada de RTVE pasa por aquí.
 *
 * Las listas de archivos y la versión las escribe la compilación (vite.config.ts):
 * cada versión nueva tiene su propia caché y la anterior se borra al activarse.
 */
const VERSION = '__BUILD_VERSION__';
const PRECACHE = ['__PRECACHE_LIST__'];
/** Lo que se carga más tarde (reproductor, Shaka): se guarda también, pero sin prisa. */
const PRECACHE_LATER = ['__PRECACHE_LATER__'];
const SHELL = `openrtve-shell-${VERSION}`;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL).then(async (cache) => {
      await cache.addAll(['./', ...PRECACHE]);
      // Prioridad baja: en la primera visita no le quita ancho de banda al catálogo y
      // las imágenes. Si algo falla, se guardará la primera vez que se use.
      await Promise.all(
        PRECACHE_LATER.map((url) =>
          fetch(url, { priority: 'low' })
            .then((response) => (response.ok ? cache.put(url, response) : undefined))
            .catch(() => {}),
        ),
      );
    }),
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith('openrtve-') && k !== SHELL).map((k) => caches.delete(k)))),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // Página: red primero (así llega la versión nueva), copia si no hay conexión.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(SHELL).then((cache) => cache.put('./', copy));
          }
          return response;
        })
        .catch(() => caches.match('./')),
    );
    return;
  }

  // Recursos de la app: de la caché si están; los que tienen hash no cambian nunca.
  event.respondWith(
    caches.match(request, { ignoreSearch: true }).then(
      (cached) =>
        cached ||
        fetch(request).then((response) => {
          if (response.ok && url.pathname.includes('/assets/')) {
            const copy = response.clone();
            caches.open(SHELL).then((cache) => cache.put(request, copy));
          }
          return response;
        }),
    ),
  );
});
