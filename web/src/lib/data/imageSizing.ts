/**
 * El servicio de imágenes de RTVE (`img.rtve.es/p/...` y `/v/...`) escala con `w`
 * y, si además llega `h`, recorta al centro a ese tamaño exacto. Así cada tarjeta
 * pide los píxeles que pinta (RtveImageSizing.kt).
 *
 * Los originales de `/imagenes/` y `/css/` no admiten `w`: se piden al
 * redimensionador del mismo host, `img.rtve.es/i/?w=&h=&i=`.
 *
 * Los `/p/` con nombre de fichero (`p/bluey_1028792.png`, logos y cuadradas de
 * Clan) no se redimensionan por ninguna de las dos vías (503 y 404): se piden tal cual
 * para no gastar una petición fallida por tarjeta.
 *
 * El ancho se redondea a escalones de [STEP_PX] para que tarjetas parecidas
 * compartan caché; el alto se escala en la misma proporción.
 */
const HOST = 'https://img.rtve.es/';
const STEP_PX = 160;
/** El servicio no entrega más de ~1900 px de ancho. */
const MAX_WIDTH_PX = 1920;

/** URL pedida al tamaño indicado, o `null` si no es del servicio redimensionable. */
export function sizedImage(url: string, widthPx: number, heightPx = 0): string | null {
  if (!url.startsWith(HOST) || widthPx <= 0) return null;
  const rest = url.slice(HOST.length);
  const queryAt = rest.indexOf('?');
  const path = queryAt >= 0 ? rest.slice(0, queryAt) : rest;
  const width = Math.min(Math.ceil(widthPx / STEP_PX) * STEP_PX, MAX_WIDTH_PX);
  const height = heightPx > 0 ? Math.max(1, Math.floor((heightPx * width) / widthPx)) : null;
  const size = [`w=${width}`, ...(height != null ? [`h=${height}`] : [])];
  if (path.startsWith('p/') && path.includes('.')) return null;
  if (path.startsWith('p/') || path.startsWith('v/') || path.startsWith('i/')) {
    const kept = (queryAt >= 0 ? rest.slice(queryAt + 1) : '')
      .split('&')
      .filter((p) => p && !p.startsWith('w=') && !p.startsWith('h='));
    return `${HOST}${path}?${[...kept, ...size].join('&')}`;
  }
  if (path.startsWith('imagenes/') || path.startsWith('css/')) {
    return `${HOST}i/?${[...size, `i=${encodeURIComponent(HOST + path)}`].join('&')}`;
  }
  return null;
}
