/**
 * Imágenes del servicio de RTVE (`img.rtve.es`) en la forma y el tamaño exactos que
 * pide la web oficial de RTVE Play.
 *
 * El servicio redimensiona al vuelo: una imagen que nadie ha pedido a ese tamaño
 * tarda de medio segundo a varios (y a veces se rinde con un 503); una que ya se ha
 * pedido sale de su caché en ~50 ms. La web oficial pide siempre las mismas pocas
 * variantes, así que las de cualquier título que se esté viendo ya están hechas. Por
 * eso aquí no se pide "el tamaño justo" sino esas variantes:
 *
 * - vídeo apaisado: `v/{id}/?w=400` (o `v/{id}/horizontal2?w=400`, la carátula
 *   editorial de películas) y `?w=1600` para lo que ocupa media pantalla o más;
 * - vídeo vertical: `v/{id}/vertical/?h=400` y `v/{id}/vertical2/?h=400`;
 * - programa: `p/{id}/imgportada/?w=400` (o `?w=1600`), `p/{id}/imgposter/?h=400`,
 *   `p/{id}/imgbackground/?h=400`… y el logo, `p/{id}/?w=320&imgProgApi=logo`.
 *
 * Sin `h=` en las apaisadas (el servicio recortaría una variante nueva) y sin
 * `srcset`: 400 px dan para una tarjeta y 1600 para la cabecera de una ficha.
 *
 * Los originales de `/imagenes/` y `/css/` pasan por el redimensionador del mismo host,
 * `img.rtve.es/i/?w=&i=`, con los mismos dos anchos. Los `/p/` con nombre de fichero
 * (`p/bluey_1028792.png`) no se redimensionan por ninguna vía: se piden tal cual.
 */
const HOST = 'https://img.rtve.es/';
/** Ancho de las tarjetas en la web oficial. */
export const CARD_PX = 400;
/** Ancho de los destacados y cabeceras en la web oficial. */
export const LARGE_PX = 1600;
/** Desde este ancho en px CSS (destacados, cabeceras) se pide la versión grande. */
const LARGE_FROM_CSS_PX = 480;

/** URL a pedir para una imagen pintada a [widthPx] px CSS, o `null` si se pide tal cual. */
export function sizedImage(url: string, widthPx: number): string | null {
  if (!url.startsWith(HOST) || widthPx <= 0) return null;
  const rest = url.slice(HOST.length);
  const queryAt = rest.indexOf('?');
  const path = queryAt >= 0 ? rest.slice(0, queryAt) : rest;
  const query = new URLSearchParams(queryAt >= 0 ? rest.slice(queryAt + 1) : '');
  const width = widthPx >= LARGE_FROM_CSS_PX ? LARGE_PX : CARD_PX;

  const video = /^v\/(\d+)(?:\/([A-Za-z0-9]+))?\/?$/.exec(path);
  if (video) {
    const [, id, variant = ''] = video;
    if (/^vertical2?$/i.test(variant)) return `${HOST}v/${id}/${variant.toLowerCase()}/?h=${CARD_PX}`;
    if (/^horizontal2$/i.test(variant)) return `${HOST}v/${id}/horizontal2?w=${width}`;
    if (variant === '' || /^horizontal$/i.test(variant)) return `${HOST}v/${id}/?w=${width}`;
    return `${HOST}v/${id}/${variant}/?w=${CARD_PX}`;
  }

  const program = /^p\/(\d+)\/?$/.exec(path);
  if (program) {
    const [, id] = program;
    const variant = (query.get('imgProgApi') ?? 'imgPortada').toLowerCase();
    if (variant === 'logo') return `${HOST}p/${id}/?w=320&imgProgApi=logo`;
    if (variant.startsWith('imgportada')) return `${HOST}p/${id}/${variant}/?w=${width}`;
    return `${HOST}p/${id}/${variant}/?h=${CARD_PX}`;
  }
  if (path.startsWith('p/')) return null;

  if (path.startsWith('i/') && query.get('i')) return `${HOST}i/?w=${width}&i=${encodeURIComponent(query.get('i')!)}`;
  if (path.startsWith('imagenes/') || path.startsWith('css/')) return `${HOST}i/?w=${width}&i=${encodeURIComponent(HOST + path)}`;
  return null;
}
