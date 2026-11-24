/**
 * Cloudflare Pages Function: reenvío de portadas de sección de RTVE Play.
 *
 * RTVE sirve `www.rtve.es/play/<sección>/index_apps.json` con CORS en unas portadas
 * (Cine, Series...) y sin él en otras (La 1, En Play, ARTE.TV...), y la respuesta no
 * depende del origen. El navegador bloquea las segundas, así que la web las pide
 * primero directamente y solo si se bloquean recurre a esta función
 * (`/rtve-proxy/portada/play/<sección>/index_apps.json`).
 *
 * Está acotada igual que la de recomendaciones:
 * - únicamente rutas `play/.../index_apps.json` de www.rtve.es, con GET y sin parámetros;
 * - solo peticiones de la propia web (otras páginas no pueden usarla de proxy);
 * - sin seguir redirecciones, para no salir de esas rutas;
 * - cada respuesta se guarda 90 segundos en la caché de Cloudflare (lo mismo que
 *   RTVE), así que RTVE recibe una petición por portada y no una por usuario.
 */

const UPSTREAM = 'https://www.rtve.es/';
const CACHE_SECONDS = 90;
const PORTADA_PATH = /^play(\/[a-z0-9-]+)+\/index_apps\.json$/;

export async function onRequestGet({ request, params, waitUntil }) {
  // Navegadores en otras webs: fuera. (Sin la cabecera, no es un navegador.)
  const site = request.headers.get('Sec-Fetch-Site');
  if (site && site !== 'same-origin' && site !== 'none') {
    return text(403, 'Solo para la propia app');
  }

  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path ?? '');
  if (!PORTADA_PATH.test(path) || new URL(request.url).search) {
    return text(403, 'Destino no permitido');
  }

  const target = `${UPSTREAM}${path}`;
  const cache = caches.default;
  const cacheKey = new Request(target);
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  let upstream;
  try {
    upstream = await fetch(target, {
      redirect: 'manual',
      headers: { Accept: 'application/json', 'User-Agent': 'Mozilla/5.0 (compatible; OpenRTVE)' },
    });
  } catch {
    return text(504, 'RTVE no responde');
  }
  if (upstream.status >= 300 && upstream.status < 400) return text(502, 'RTVE redirige fuera de la portada');

  const response = new Response(upstream.body, {
    status: upstream.status,
    headers: {
      'Content-Type': upstream.headers.get('Content-Type') ?? 'application/json',
      'Cache-Control': upstream.ok ? `public, max-age=${CACHE_SECONDS}` : 'no-store',
      'X-Content-Type-Options': 'nosniff',
    },
  });
  if (upstream.ok) waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

function text(status, body) {
  return new Response(body, { status, headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' } });
}
