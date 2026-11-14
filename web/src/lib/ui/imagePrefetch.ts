import { sizedImage } from '../data/imageSizing';
import { imageFor, type CatalogItem } from '../domain/models';
import { showsPosters, type ProgramTab } from './sections';

/*
 * El servicio de imágenes de RTVE redimensiona al vuelo: la primera petición de cada
 * imagen tarda de medio segundo a dos, y las siguientes salen de su caché al momento.
 * Las variantes que usa la web oficial casi siempre están ya hechas, pero no todas:
 * lo que probablemente se vaya a ver (las otras pestañas de una ficha) se pide antes,
 * sin prisa, con la misma URL que usará la tarjeta, y al llegar ya está en la caché.
 */

const requested = new Set<string>();
/** Referencias a las descargas en curso, para que no se cancelen al recolectarse. */
const loading = new Set<HTMLImageElement>();

export function prefetchArtwork(url: string | null | undefined, width: number) {
  if (!url || typeof Image === 'undefined') return;
  const key = sizedImage(url, width) ?? url;
  if (requested.has(key)) return;
  requested.add(key);
  if (requested.size > 1000) requested.delete(requested.values().next().value!);

  const img = new Image();
  img.decoding = 'async';
  img.fetchPriority = 'low';
  loading.add(img);
  img.onload = img.onerror = () => loading.delete(img);
  img.src = key;
}

/** La primera pantalla de una pestaña de ficha, con la forma en que la pinta. */
export function prefetchTabImages(tab: ProgramTab, items: CatalogItem[], count = 12) {
  const posters = tab === 'RELATED' || showsPosters(tab);
  for (const item of items.slice(0, count)) {
    if (posters) prefetchArtwork(imageFor(item, 'POSTER'), 220);
    else prefetchArtwork(item.imageUrl, 200);
  }
}

export { whenIdle } from './idle';
