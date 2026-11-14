import {
  collectionRow,
  linkApiId,
  liveRow,
  referenceItem,
  type CatalogItem,
  type ExploreCategory,
  type HomeLink,
  type HomeRow,
} from '../domain/models';
import { resolvePlayback } from '../domain/playback';
import { repository } from '../data/repository';
import { toLoadError } from '../data/http';
import { favorites } from '../data/favorites.svelte';
import { watchHistory } from '../data/watchHistory.svelte';
import { messages } from './messages.svelte';
import { nav, rememberItem } from './nav.svelte';
import { blockedText, errorText, scheduleLabel } from './text';

/*
 * Lo que la UI puede hacer con el catálogo (CatalogActions.kt): abrir fichas,
 * reproducir (o explicar por qué no se puede) y mostrar avisos.
 */

export function play(item: CatalogItem, restart = false) {
  const decision = resolvePlayback(item);
  if (decision.type === 'blocked') {
    const schedule = item.live ? scheduleLabel(item.live, Date.now()) : null;
    messages.show(decision.reason === 'NOT_STARTED_YET' && schedule ? `Empieza ${schedule}` : blockedText(decision.reason));
    return;
  }
  if (item.kind === 'LIVE') {
    rememberItem(`live:${item.assetId}`, item);
    nav.navigate({ name: 'live', assetId: item.assetId!, drm: item.drm, title: item.title });
    return;
  }
  watchHistory.register(item);
  const id = item.playbackId ?? item.id;
  rememberItem(`play:${id}`, item);
  nav.navigate({ name: 'play', id, restart });
}

/** Programas y vídeos abren su ficha; los directos se reproducen al momento. */
export function openItem(item: CatalogItem) {
  if (item.kind === 'PROGRAM') {
    rememberItem(`program:${item.id}`, item);
    nav.navigate({ name: 'program', id: item.id, title: item.title });
  } else if (item.kind === 'VIDEO') {
    rememberItem(`video:${item.id}`, item);
    nav.navigate({ name: 'video', id: item.id, title: item.title });
  } else {
    play(item);
  }
}

export function openRow(row: HomeRow, title: string) {
  if (!row.contentUrl) return;
  nav.navigate({ name: 'module', url: row.contentUrl, presentation: row.presentation, moduleType: row.moduleType, title });
}

/** Programa y vídeo necesitan el id de su URL de API; si no se reconoce, el enlace no lleva a ningún sitio. */
export function openLink(link: HomeLink) {
  switch (link.kind) {
    case 'COLLECTION': return openRow(collectionRow(link.url, link.title), link.title);
    case 'PORTADA': return nav.navigate({ name: 'portada', url: link.url, title: link.title });
    case 'PROGRAM':
    case 'VIDEO': {
      const id = linkApiId(link);
      if (id) openItem(referenceItem(id, link.kind, link.title, link.imageUrl));
    }
  }
}

/** Una categoría abre su portada; un canal temático, la lista con su directo. */
export function openCategory(category: ExploreCategory) {
  if (category.isLive) openRow(liveRow(category.contentUrl, category.title), category.title);
  else nav.navigate({ name: 'portada', url: category.contentUrl, title: category.title });
}

export function openProgram(programId: string, title: string) {
  openItem(referenceItem(programId, 'PROGRAM', title));
}

/** Quita un item de "Seguir viendo" y ofrece deshacerlo. */
export function removeFromHistory(item: CatalogItem) {
  const removed = watchHistory.remove(item.id);
  if (removed) {
    messages.show(`«${removed.item.title}» quitado de Seguir viendo`, {
      action: { label: 'Deshacer', run: () => watchHistory.restore(removed) },
    });
  }
}

/** Quita un título de "Mi lista" y ofrece deshacerlo. */
export function removeFromMyList(item: CatalogItem) {
  const removed = favorites.remove(item.id);
  if (removed) {
    messages.show(`«${removed.item.title}» quitado de Mi lista`, {
      action: { label: 'Deshacer', run: () => favorites.restore(removed) },
    });
  }
}

/** Enlace recibido (compartir, pegar en el buscador): construye la ficha o reproduce. */
export async function openIncomingLink(url: string, { replace = false } = {}) {
  const id = messages.show('Abriendo enlace…', { persistent: true });
  let item: CatalogItem | null;
  try {
    item = await repository.resolveLink(url);
  } catch (error) {
    messages.show(errorText(toLoadError(error)));
    if (replace) nav.navigate({ name: 'home' }, { replace: true });
    return;
  }
  if (!item) {
    messages.show('No se ha podido abrir este enlace de RTVE');
    if (replace) nav.navigate({ name: 'home' }, { replace: true });
    return;
  }
  messages.dismiss(id);
  if (replace) nav.navigate({ name: 'home' }, { replace: true });
  openItem(item);
}

/** Compartir: la hoja del sistema si existe; si no, al portapapeles. */
export async function share(title: string, url: string) {
  try {
    if (navigator.share) {
      await navigator.share({ title, url });
      return;
    }
    await navigator.clipboard.writeText(url);
    messages.show('Enlace copiado');
  } catch (error) {
    if ((error as DOMException)?.name !== 'AbortError') messages.show('No se pudo compartir');
  }
}
