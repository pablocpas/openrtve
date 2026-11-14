/*
 * Service worker de OpenRTVE. Guarda la propia app para que arranque sin red (el
 * catálogo ya vive en IndexedDB y el vídeo nunca se guarda) y las imágenes de RTVE,
 * como la caché de disco de Coil en Android: el servicio de imágenes tarda de medio
 * segundo a dos en servir cada una la primera vez y la caché del navegador solo las
 * guarda 6 horas, sin forma de revalidarlas. Lo demás de RTVE no pasa por aquí.
 *
 * Las listas de archivos y la versión las escribe la compilación (vite.config.ts):
 * cada versión nueva tiene su propia caché y la anterior se borra al activarse.
 */
const VERSION = '__BUILD_VERSION__';
const PRECACHE = ['__PRECACHE_LIST__'];
/** Lo que se carga más tarde (reproductor, Shaka): se guarda también, pero sin prisa. */
const PRECACHE_LATER = ['__PRECACHE_LATER__'];
const SHELL = `openrtve-shell-${VERSION}`;
/** Sin versión: las imágenes sobreviven a las actualizaciones. Ajustes > Borrar caché la vacía. */
const IMAGES = 'openrtve-runtime-images';
/** Unas 1.500 imágenes a su tamaño de tarjeta son del orden de 100 MB. */
const MAX_IMAGES = 1500;
const IMAGE_HOST = 'img.rtve.es';

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
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith('openrtve-shell-') && k !== SHELL).map((k) => caches.delete(k)))),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.hostname === IMAGE_HOST && request.destination === 'image') {
    event.respondWith(cachedImage(request, event));
    return;
  }
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

/**
 * Imagen de RTVE: de la caché si ya se vio; si no, de la red y se guarda. Se pide con
 * CORS (RTVE lo permite) para guardar una respuesta normal y no una opaca, que el
 * navegador contaría como varios MB. Si CORS fallase, se pide como la pidió la página.
 */
async function cachedImage(request, event) {
  let cache;
  try {
    cache = await caches.open(IMAGES);
    const hit = await cache.match(request.url);
    if (hit) return hit;
  } catch {
    // Sin Cache Storage: directa a la red.
  }
  // La precarga y la tarjeta pueden pedir la misma imagen a la vez: una sola descarga.
  let pending = inFlightImages.get(request.url);
  if (!pending) {
    pending = fetch(request.url, { mode: 'cors', credentials: 'omit', referrerPolicy: 'no-referrer' }).then((response) => {
      // Se guarda aparte: la imagen se pinta según llega, sin esperar a la caché.
      if (cache && response.ok) event.waitUntil(cache.put(request.url, response.clone()).then(() => trimImages(cache)).catch(() => {}));
      return response;
    });
    pending.finally(() => inFlightImages.delete(request.url)).catch(() => {});
    inFlightImages.set(request.url, pending);
  }
  try {
    return (await pending).clone();
  } catch {
    return fetch(request);
  }
}

const inFlightImages = new Map();

let putsSinceTrim = 0;

/** Cada cierto número de imágenes nuevas, se quitan las más antiguas por encima del tope. */
async function trimImages(cache) {
  if (++putsSinceTrim < 50) return;
  putsSinceTrim = 0;
  const keys = await cache.keys();
  await Promise.all(keys.slice(0, Math.max(0, keys.length - MAX_IMAGES)).map((key) => cache.delete(key)));
}
