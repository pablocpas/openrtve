import {
  isCollection,
  isMovie,
  mediaForm,
  mediaSubject,
  type ProgramStructure,
} from '../domain/classification';
import type { EpisodeOrder } from '../domain/episodeOrder';
import type { CatalogItem, ProgramDetail, VideoDetail } from '../domain/models';
import type { LoadError } from '../data/http';
import { isInProgress, type WatchEntry } from '../data/watchHistory.svelte';
import { countryName, feedDateToDisplay, languageName, MAX_HEADER_GENRES } from './text';

/*
 * Secciones de las fichas (ContentSections.kt, ProgramTabs.kt, PlaySuggestion.kt).
 * La API entrega dos listas paginadas por programa (completos y extras); cada pieza
 * va a la sección que le corresponde por su forma, su tema y la estructura del
 * programa, sin deducir nada del título.
 */

export type ProgramTab = 'EPISODES' | 'MOVIES' | 'DOCUMENTARIES' | 'VIDEOS' | 'MORE_VIDEOS' | 'RELATED';

export const TAB_TITLES: Record<ProgramTab, string> = {
  EPISODES: 'Capítulos',
  MOVIES: 'Películas',
  DOCUMENTARIES: 'Documentales',
  VIDEOS: 'Vídeos',
  MORE_VIDEOS: 'Extras',
  RELATED: 'Similares',
};

/** Películas y documentales se recorren como pósters; el resto, como lista. */
export const showsPosters = (tab: ProgramTab) => tab === 'MOVIES' || tab === 'DOCUMENTARIES';

export function primaryTab(structure: ProgramStructure): ProgramTab {
  switch (structure) {
    case 'EPISODIC': return 'EPISODES';
    case 'MOVIES': return 'MOVIES';
    case 'DOCUMENTARIES': return 'DOCUMENTARIES';
    case 'VIDEOS': return 'VIDEOS';
  }
}

/** En un contenedor, o si es una película, cada pieza abre su ficha; un capítulo o un extra se reproduce. */
export const opensDetailsIn = (item: CatalogItem, structure: ProgramStructure) =>
  item.kind === 'PROGRAM' || (mediaForm(item) !== 'EXTRA' && (isCollection(structure) || isMovie(item)));

/** Sección de una pieza: primero su forma, luego su tema y, por último, la estructura. */
export function contentTab(item: CatalogItem, structure: ProgramStructure, fromExtras = false): ProgramTab {
  const form = mediaForm(item);
  if (form === 'EXTRA' || (form === 'UNKNOWN' && fromExtras)) return 'MORE_VIDEOS';
  if (isMovie(item)) return 'MOVIES';
  if (isCollection(structure) && mediaSubject(item) === 'DOCUMENTARY') return 'DOCUMENTARIES';
  if (isCollection(structure)) return 'VIDEOS';
  return 'EPISODES';
}

/** Una lista paginada de una ficha: sus piezas, por dónde va y cómo está su carga. */
export interface PagedItemsState {
  items: CatalogItem[];
  page: number;
  totalPages: number;
  isLoading: boolean;
  isLoadingMore: boolean;
  error: LoadError | null;
}

export const emptyPaged = (isLoading = false): PagedItemsState => ({
  items: [], page: 0, totalPages: 0, isLoading, isLoadingMore: false, error: null,
});

export const pagedHasMore = (s: PagedItemsState) => s.page < s.totalPages;

export interface ContentSection {
  tab: ProgramTab;
  items: CatalogItem[];
  loadsComplete: boolean;
  loadsExtras: boolean;
  isLoading: boolean;
  isLoadingMore: boolean;
  hasMore: boolean;
  error: LoadError | null;
}

/** Lo que se elige en Extras se reproduce; en el resto, según la pieza y la estructura. */
export const sectionOpensDetails = (section: ContentSection, item: CatalogItem, structure: ProgramStructure) =>
  section.tab !== 'MORE_VIDEOS' && opensDetailsIn(item, structure);

/**
 * Reparte las dos fuentes en secciones, en el orden de las fichas. [includePrimary]
 * mantiene la sección principal aunque aún no tenga piezas, para que la ficha de un
 * programa explique siempre qué pasa.
 */
export function contentSections(
  structure: ProgramStructure,
  complete: PagedItemsState,
  extras: PagedItemsState,
  includePrimary: boolean,
): ContentSection[] {
  const groups = new Map<ProgramTab, CatalogItem[]>();
  const fromComplete = new Set<ProgramTab>();
  const fromExtras = new Set<ProgramTab>();
  const seen = new Set<string>();
  const add = (tab: ProgramTab) => {
    if (!groups.has(tab)) groups.set(tab, []);
    return groups.get(tab)!;
  };
  for (const [page, isExtras] of [[complete, false], [extras, true]] as const) {
    for (const item of page.items) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      const tab = contentTab(item, structure, isExtras);
      add(tab).push(item);
      (isExtras ? fromExtras : fromComplete).add(tab);
    }
  }
  const primary = primaryTab(structure);
  const completePending = complete.isLoading || complete.error != null || (pagedHasMore(complete) && complete.items.length === 0);
  if (completePending || (includePrimary && (pagedHasMore(complete) || groups.size === 0))) add(primary);
  if (extras.isLoading || extras.error != null || (pagedHasMore(extras) && extras.items.length === 0)) add('MORE_VIDEOS');
  const order = [...new Set<ProgramTab>([primary, 'EPISODES', 'MOVIES', 'DOCUMENTARIES', 'VIDEOS', 'MORE_VIDEOS'])];
  return order.filter((tab) => groups.has(tab)).map((tab) => {
    const loadsComplete = tab !== 'MORE_VIDEOS' || fromComplete.has(tab);
    const loadsExtras = fromExtras.has(tab) || tab === 'MORE_VIDEOS';
    const sources = [loadsComplete ? complete : null, loadsExtras ? extras : null].filter((s): s is PagedItemsState => s != null);
    return {
      tab,
      items: groups.get(tab)!,
      loadsComplete,
      loadsExtras,
      isLoading: sources.some((s) => s.isLoading),
      isLoadingMore: sources.some((s) => s.isLoadingMore),
      hasMore: sources.some(pagedHasMore),
      error: sources.find((s) => s.error)?.error ?? null,
    };
  });
}

/** Qué debe hacer el botón grande de una ficha de programa. */
export type PlaySuggestion =
  | { type: 'play'; item: CatalogItem }
  | { type: 'continue'; item: CatalogItem; entry: WatchEntry }
  | { type: 'next'; item: CatalogItem };

/**
 * Sin historial: el primero de la lista. A medias: continuar. Terminado: el
 * siguiente cronológico; si no está en la lista, el más reciente cuando hay uno más
 * nuevo que el visto, o el primero de la lista.
 */
export function suggestPlay(
  entries: WatchEntry[],
  programId: string,
  episodes: CatalogItem[],
  order: EpisodeOrder,
  accepts: (item: CatalogItem) => boolean = () => true,
): PlaySuggestion | null {
  const eligible = episodes.filter(accepts);
  const last = entries
    .filter((e) => e.item.programId === programId && (isInProgress(e) || e.finished) && accepts(e.item))
    .reduce<WatchEntry | null>((best, e) => (!best || e.updatedAt > best.updatedAt ? e : best), null);
  const start = eligible[0];
  const newestFirst = order === 'NEWEST_FIRST';
  if (!last) return start ? { type: 'play', item: start } : null;
  if (isInProgress(last)) return { type: 'continue', item: last.item, entry: last };
  const index = eligible.findIndex((it) => it.id === last.item.id);
  const following = index < 0 ? -1 : newestFirst ? index - 1 : index + 1;
  if (following >= 0 && following < eligible.length) return { type: 'next', item: eligible[following] };
  if (newestFirst && start && start.id !== last.item.id) return { type: 'next', item: start };
  return start ? { type: 'play', item: start } : null;
}

export function suggestionLabel(s: PlaySuggestion): string {
  const verb = s.type === 'continue' ? 'Continuar' : s.type === 'next' ? 'Siguiente' : 'Reproducir';
  return `${verb} · ${s.item.title}`;
}

/** Etiqueta de una pieza en su sección: dónde está el espectador o su tipo. */
export function sectionBadge(
  section: ContentSection,
  item: CatalogItem,
  entry: WatchEntry | undefined,
  suggestion: PlaySuggestion | null,
  currentItemId: string | null,
): string | null {
  if (section.tab !== 'EPISODES') return section.tab === 'MORE_VIDEOS' ? item.mediaTypeName ?? null : item.mediaSubtypeName ?? null;
  if (item.id === currentItemId) return 'Este capítulo';
  if (entry?.finished) return 'Visto';
  if (suggestion?.type === 'next' && suggestion.item.id === item.id) return 'Siguiente';
  return null;
}

/** Ficha técnica: lo que no va en la línea de datos de la cabecera. */
export interface FactRow {
  label: string;
  value: string;
}

export interface Facts {
  rows: FactRow[];
  /** Cuántas de las primeras filas (reparto, dirección) se ven con la sinopsis plegada. */
  compactRowCount: number;
}

function facts(input: {
  cast: string[]; director: string | null; genres: string[]; originalLanguage: string | null;
  country?: string | null; expirationDate?: string | null;
}): Facts {
  const rows: FactRow[] = [];
  if (input.cast.length) rows.push({ label: 'Reparto', value: input.cast.join(', ') });
  if (input.director?.trim()) rows.push({ label: 'Dirección', value: input.director });
  const genres = input.genres.length > MAX_HEADER_GENRES ? input.genres : [];
  if (genres.length) rows.push({ label: 'Género', value: genres.join(', ') });
  if (input.originalLanguage) rows.push({ label: 'Idioma original', value: languageName(input.originalLanguage) });
  if (input.country) rows.push({ label: 'País', value: countryName(input.country) });
  if (input.expirationDate) rows.push({ label: 'Disponible hasta', value: feedDateToDisplay(input.expirationDate) });
  return { rows, compactRowCount: [input.cast.length > 0, !!input.director?.trim()].filter(Boolean).length };
}

export const videoFacts = (d: VideoDetail) => facts(d);
export const programFacts = (d: ProgramDetail) => facts(d);
