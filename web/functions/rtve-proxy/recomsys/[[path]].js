/**
 * Cloudflare Pages Function: reenvío de las recomendaciones de RTVE.
 *
 * `recomsys.rtve.es` (Similares, búsquedas predefinidas y rankings) solo responde a
 * páginas de `www.rtve.es`, así que el navegador no puede pedirlo directamente. La app
 * lo pide aquí (`/rtve-proxy/recomsys/recommendation/...`) y esta función lo reenvía.
 *
 * Solo esto se ejecuta en Cloudflare; el resto de la web es estático. Está acotada:
 * - únicamente rutas `recommendation/<tipo>` de recomsys, con GET;
 * - solo peticiones de la propia web (otras páginas no pueden usarla de proxy);
 * - cada respuesta se guarda 5 minutos en la caché de Cloudflare, así que RTVE recibe
 *   una petición por lista y no una por usuario.
 */

const UPSTREAM = 'https://recomsys.rtve.es/';
const CACHE_SECONDS = 300;
const MAX_QUERY_LENGTH = 512;
const SAFE_QUERY = /^[A-Za-z0-9\-._~%&=,/:+]*$/;

export async function onRequestGet({ request, params, waitUntil }) {
  // Navegadores en otras webs: fuera. (Sin la cabecera, no es un navegador.)
  const site = request.headers.get('Sec-Fetch-Site');
  if (site && site !== 'same-origin' && site !== 'none') {
    return text(403, 'Solo para la propia app');
  }

  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path ?? '');
  const query = new URL(request.url).search.slice(1);
  if (!/^recommendation\/[a-z0-9_-]+$/i.test(path) || query.length > MAX_QUERY_LENGTH || !SAFE_QUERY.test(query)) {
    return text(403, 'Destino no permitido');
  }

  const target = `${UPSTREAM}${path}${query ? `?${query}` : ''}`;
  const cache = caches.default;
  const cacheKey = new Request(target);
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  let upstream;
  try {
    upstream = await fetch(target, {
      headers: {
        Origin: 'https://www.rtve.es',
        Referer: 'https://www.rtve.es/',
        Accept: 'application/json',
        'User-Agent': 'Mozilla/5.0 (compatible; OpenRTVE)',
      },
    });
  } catch {
    return text(504, 'RTVE no responde');
  }

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
