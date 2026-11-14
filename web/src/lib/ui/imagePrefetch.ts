import { artworkCandidates, toSrcset } from '../data/imageSizing';
import { imageFor, type CatalogItem } from '../domain/models';
import { showsPosters, type ProgramTab } from './sections';

/*
 * El servicio de imágenes de RTVE redimensiona al vuelo: la primera petición de cada
 * imagen tarda de medio segundo a dos, y las siguientes salen de su caché al momento.
 * Por eso lo que probablemente se vaya a ver (las otras pestañas de una ficha) se
 * pide antes, sin prisa, con las mismas URL que usará la tarjeta: al llegar, ya está
 * en la caché del navegador. Es lo que en Android hace la caché de disco de Coil.
 */

const requested = new Set<string>();
/** Referencias a las descargas en curso, para que no se cancelen al recolectarse. */
const loading = new Set<HTMLImageElement>();

export function prefetchArtwork(url: string | null | undefined, ratio: number, width: number) {
  if (!url || typeof Image === 'undefined') return;
  const candidates = artworkCandidates(url, ratio, width);
  const key = candidates.length ? candidates[0].src : url;
  if (requested.has(key)) return;
  requested.add(key);
  if (requested.size > 1000) requested.delete(requested.values().next().value!);

  const img = new Image();
  img.decoding = 'async';
  img.fetchPriority = 'low';
  // Como en la tarjeta: el navegador elige la misma versión para esta pantalla.
  if (candidates.length > 1) {
    img.sizes = `${width}px`;
    img.srcset = toSrcset(candidates);
  }
  loading.add(img);
  img.onload = img.onerror = () => loading.delete(img);
  img.src = candidates[0]?.src ?? url;
}

/** La primera pantalla de una pestaña de ficha, con la forma en que la pinta. */
export function prefetchTabImages(tab: ProgramTab, items: CatalogItem[], count = 12) {
  const posters = tab === 'RELATED' || showsPosters(tab);
  for (const item of items.slice(0, count)) {
    if (posters) prefetchArtwork(imageFor(item, 'POSTER'), 2 / 3, 220);
    else prefetchArtwork(item.imageUrl, 16 / 9, 200);
  }
}

/** Ejecuta [task] cuando el navegador quede libre; devuelve cómo cancelarlo. */
export function whenIdle(task: () => void): () => void {
  if (typeof requestIdleCallback === 'function') {
    const handle = requestIdleCallback(task, { timeout: 1500 });
    return () => cancelIdleCallback(handle);
  }
  const handle = setTimeout(task, 300);
  return () => clearTimeout(handle);
}
