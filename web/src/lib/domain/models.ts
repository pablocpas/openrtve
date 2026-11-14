/*
 * Modelo del catálogo, el mismo que la app de Android (CatalogModels.kt).
 * Todo se deduce de los feeds públicos de RTVE: no hay listas compiladas.
 */

export const RtveUrls = {
  TV_HOME: 'https://www.rtve.es/play/index_apps.json',
  REMOTE_CONFIG: 'https://www.rtve.es/m/configs/rtve_play/2026/estructura2.json',
  /** La que lee la app oficial de Android TV: su propio menú y sus propias imágenes. */
  REMOTE_CONFIG_TV: 'https://www.rtve.es/m/configs/rtve_play/2026/estructuraAndroidTV2.json',
  QUICK_SEARCHES: 'https://www.rtve.es/m/configs/buscador/predefinidos.json',
  SEARCH: 'https://api.rtve.es/api/search/results',
  /** Base de las páginas siguientes de cada bloque de búsqueda: se le añade su `query` relativo. */
  SEARCH_PAGES: 'https://api.rtve.es/api/search',
  RECOMMENDATIONS: 'https://recomsys.rtve.es/recommendation',
  PROGRAMS: 'https://www.rtve.es/api/programas',
  VIDEOS: 'https://api.rtve.es/api/videos',
  LIVES: 'https://api.rtve.es/api/lives',
  PREVIEWS: 'https://videopreviews.rtve.es/tiivii-previews/api',
} as const;

/** Recomendaciones editoriales de RTVE (`recomsys`): "Similares" de una ficha. */
export type RelatedKind = 'program' | 'film' | 'documental';
export const RELATED_PREFIX: Record<RelatedKind, string> = { program: 'pr', film: 'v', documental: 'v' };

/**
 * Cómo pinta la UI una fila; se deduce del `tipo` editorial. Cada valor es una
 * presentación distinta de RTVE Play.
 */
export type RowLayout = 'HERO' | 'FEATURED' | 'POSTER' | 'POSTER_TALL' | 'SQUARE' | 'LANDSCAPE' | 'RANKED';

/** Tarjetas más altas que anchas. */
export const isVertical = (layout: RowLayout) => layout === 'POSTER' || layout === 'POSTER_TALL';

/** Proporción ancho/alto de la imagen de cada tarjeta. */
export function aspectRatio(layout: RowLayout): number {
  switch (layout) {
    case 'POSTER': return 2 / 3;
    case 'POSTER_TALL': return 1 / 2;
    case 'SQUARE': return 1;
    default: return 16 / 9;
  }
}

/** En rejilla los destacados no tienen sentido: se degradan a apaisada. */
export const gridLayout = (layout: RowLayout): RowLayout =>
  layout === 'HERO' || layout === 'FEATURED' ? 'LANDSCAPE' : layout;

export type ContentKind = 'PROGRAM' | 'VIDEO' | 'LIVE' | 'UNKNOWN';

/**
 * Lo que el feed de directos sabe de la emisión. Las horas del feed son de Madrid
 * y aquí ya van en milisegundos UTC.
 */
export interface LiveInfo {
  isOnAir: boolean;
  startsAtMillis: number | null;
  durationMinutes: number | null;
  progressPercent: number | null;
  channelLogoUrl: string | null;
  category: string | null;
}

export function liveProgressAt(live: LiveInfo, now: number): number | null {
  const { startsAtMillis: start, durationMinutes: minutes } = live;
  if (start != null && minutes != null && minutes > 0) {
    return clamp01((now - start) / (minutes * 60_000));
  }
  return live.progressPercent != null ? clamp01(live.progressPercent / 100) : null;
}

/** Programado y aún no empezado según el reloj. */
export const isUpcomingAt = (live: LiveInfo, now: number) =>
  !live.isOnAir && live.startsAtMillis != null && live.startsAtMillis > now;

export const clamp01 = (value: number) => Math.min(1, Math.max(0, value));

export interface CatalogItem {
  id: string;
  /** ID del medio reproducible; `null` si el item no apunta a ningún medio. */
  playbackId: string | null;
  assetId: string | null;
  title: string;
  subtitle: string | null;
  /** Imagen apaisada. */
  imageUrl: string | null;
  kind: ContentKind;
  /** Póster vertical 2:3. */
  posterUrl?: string | null;
  /** Imagen cuadrada 1:1. */
  squareUrl?: string | null;
  /** Recorte alto (1:2) `imgCol` de un programa. */
  tallPosterUrl?: string | null;
  allowedInCountry: boolean | null;
  loginRequired: boolean;
  paid: boolean;
  drm: boolean;
  programId?: string | null;
  seasonId?: string | null;
  /** Tipo editorial (`39816` completo, `39978` fragmento, `39980` avance). */
  mediaTypeId?: string | null;
  mediaTypeName?: string | null;
  mediaSubtypeName?: string | null;
  programShowsFragments?: boolean | null;
  durationMs?: number | null;
  /** Fecha tal como la entrega el feed (`dd-MM-yyyy HH:mm:ss`). */
  publicationDate?: string | null;
  episode?: number | null;
  seasonTitle?: string | null;
  live?: LiveInfo | null;
  webUrl?: string | null;
  /** `subType.id`; `130734` identifica una película. */
  mediaSubtypeId?: string | null;
  programTypeId?: string | null;
  programType?: string | null;
}

/** Qué recorte pinta cada presentación, con sus respaldos. */
export function imageFor(item: CatalogItem, layout: RowLayout): string | null {
  switch (layout) {
    case 'POSTER': return item.posterUrl ?? item.imageUrl;
    case 'POSTER_TALL': return item.tallPosterUrl ?? item.posterUrl ?? item.imageUrl;
    case 'SQUARE': return item.squareUrl ?? item.imageUrl ?? item.posterUrl ?? null;
    default: return item.imageUrl;
  }
}

/** Item mínimo que solo aporta id y tipo: la pantalla de destino carga la ficha completa. */
export function referenceItem(id: string, kind: ContentKind, title: string, imageUrl: string | null = null): CatalogItem {
  return {
    id,
    playbackId: kind === 'PROGRAM' ? null : id,
    assetId: null,
    title,
    subtitle: null,
    imageUrl,
    kind,
    allowedInCountry: null,
    loginRequired: false,
    paid: false,
    drm: false,
    programId: kind === 'PROGRAM' ? id : null,
  };
}

export interface HomeFeed {
  id: string | null;
  title: string;
  rows: HomeRow[];
}

export type LinkKind = 'COLLECTION' | 'PORTADA' | 'PROGRAM' | 'VIDEO';

/** Acceso editorial de una fila `links`: viene completo en la portada. */
export interface HomeLink {
  title: string;
  imageUrl: string | null;
  url: string;
  kind: LinkKind;
}

/** Último segmento numérico de la URL de la API (`/api/programas/1234.json` -> `1234`). */
export const linkApiId = (link: HomeLink) => /\/(\d+)(?:\.json)?\/?$/.exec(link.url)?.[1] ?? null;

export interface HomeRow {
  /** Posición estable dentro de la portada. */
  id: number;
  title: string;
  order: number;
  moduleType: string | null;
  presentation: string | null;
  contentUrl: string | null;
  layout: RowLayout;
  links: HomeLink[];
}

export const isInlineRow = (row: HomeRow) => row.links.length > 0;
export const isRenderableRow = (row: HomeRow) => isInlineRow(row) || row.contentUrl != null;
export const isLiveRow = (row: HomeRow) =>
  (row.presentation?.toLowerCase().startsWith('directos') ?? false) ||
  row.moduleType?.toLowerCase() === 'livescollection';

/** Fila sintética de colección, para abrirla desde un enlace. */
export const collectionRow = (url: string, title: string): HomeRow => ({
  id: -1, title, order: 0, moduleType: 'Collection', presentation: 'ColeccionPoster', contentUrl: url, layout: 'POSTER', links: [],
});

/** Fila sintética de directos sobre un feed de `lives`. */
export const liveRow = (url: string, title = ''): HomeRow => ({
  id: -1, title, order: 0, moduleType: 'livesCollection', presentation: 'directosTV', contentUrl: url, layout: 'LANDSCAPE', links: [],
});

export interface CatalogModule {
  title: string;
  items: CatalogItem[];
}

/** "Ver todo" solo tiene sentido si la fila trae más de lo que cabe de un vistazo. */
export const SEE_ALL_MIN_ITEMS = 6;

export interface CatalogPage {
  items: CatalogItem[];
  page: number;
  totalPages: number;
}

export interface ProgramSeason {
  id: string;
  title: string;
  episodeCount: number | null;
  order: number | null;
}

export interface ProgramDetail {
  id: string;
  title: string;
  /** Puede contener HTML. */
  description: string | null;
  imageUrl: string | null;
  emission: string | null;
  /** Ya en el orden en que se muestran. */
  seasons: ProgramSeason[];
  webUrl: string | null;
  programTypeId: string | null;
  inEmission: boolean;
  isComplete: boolean;
  showFragments: boolean;
  year: string | null;
  ageRating: string | null;
  genres: string[];
  director: string | null;
  cast: string[];
  originalLanguage: string | null;
  country: string | null;
  programType: string | null;
}

export interface CatalogLoad<T> {
  value: T;
  isStale: boolean;
}

export interface SearchResults {
  programs: CatalogItem[];
  videos: CatalogItem[];
  videosPage: number;
  videosTotalPages: number;
  videosQuery: string | null;
}

export const hasMoreVideos = (r: SearchResults) => r.videosQuery != null && r.videosPage < r.videosTotalPages;

/** Pestaña de búsqueda predefinida ("Más buscados", "Series TV"...). */
export interface QuickFilter {
  title: string;
  contentUrl: string;
}

/** Entrada del menú de Explorar: una portada, o un canal temático en directo. */
export interface ExploreCategory {
  title: string;
  imageUrl: string | null;
  contentUrl: string;
  isLive: boolean;
  /** Imagen de la misma portada en la otra configuración, por si la propia no carga. */
  fallbackImageUrl: string | null;
  /** Lo que emite ahora un canal temático. */
  nowPlaying: CatalogItem | null;
}

export interface ExploreGroup {
  title: string | null;
  categories: ExploreCategory[];
  isKids: boolean;
}

/** Ficha completa de un vídeo (`videos/{id}.json`). */
export interface VideoDetail {
  item: CatalogItem;
  backdropUrl: string | null;
  description: string | null;
  promo: string | null;
  programTitle: string | null;
  year: string | null;
  ageRating: string | null;
  genres: string[];
  director: string | null;
  cast: string[];
  originalLanguage: string | null;
  expirationDate: string | null;
  webUrl: string | null;
}

/** Miniaturas de la barra de progreso: un sprite y las regiones de cada tramo. */
export interface PreviewSprite {
  imageUrl: string;
  cues: SpriteCue[];
}

export interface SpriteCue {
  startMs: number;
  endMs: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export function cueAt(sprite: PreviewSprite, positionMs: number): SpriteCue | null {
  const hit = sprite.cues.find((cue) => positionMs >= cue.startMs && positionMs < cue.endMs);
  if (hit) return hit;
  const last = sprite.cues[sprite.cues.length - 1];
  return last && positionMs >= last.endMs ? last : null;
}
