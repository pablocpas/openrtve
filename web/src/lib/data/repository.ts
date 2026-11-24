import type { EpisodeOrder } from '../domain/episodeOrder';
import { orderApiValue } from '../domain/episodeOrder';
import { RtveTypes } from '../domain/classification';
import { parseDeepLink } from '../domain/deepLink';
import {
  RELATED_PREFIX,
  RtveUrls,
  liveRow,
  referenceItem,
  type CatalogItem,
  type CatalogLoad,
  type CatalogModule,
  type CatalogPage,
  type ExploreGroup,
  type HomeFeed,
  type HomeRow,
  type PreviewSprite,
  type ProgramDetail,
  type QuickFilter,
  type RelatedKind,
  type SearchResults,
  type VideoDetail,
} from '../domain/models';
import { documentCache } from './documentCache';
import { getText, isAbort } from './http';
import * as parser from './parser';

/*
 * Acceso al catálogo (CatalogRepository.kt). Cada documento tiene un tiempo en que
 * se considera fresco (se usa sin red) y otro en que, si la red falla, aún se
 * muestra marcado como antiguo.
 */

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const HOME = { fresh: 2 * MIN, stale: DAY };
const MODULE = { fresh: 5 * MIN, stale: DAY };
const PROGRAM = { fresh: 15 * MIN, stale: 7 * DAY };
const CONFIG = { fresh: HOUR, stale: 7 * DAY };
const MAX_RELATED = 24;

export class NotCachedError extends Error {}

interface LoadOptions {
  forceRefresh?: boolean;
  cachedOnly?: boolean;
  signal?: AbortSignal;
}

/** Una misma URL pedida a la vez por dos pantallas se descarga una sola vez. */
const inFlight = new Map<string, Promise<string>>();

function fetchShared(url: string, revalidate: boolean, signal?: AbortSignal): Promise<string> {
  const key = `${revalidate ? 'r' : 'd'}:${url}`;
  let pending = inFlight.get(key);
  if (!pending) {
    // La descarga compartida no se cancela con una pantalla: la otra puede seguir esperándola.
    pending = getText(url, { revalidate }).finally(() => inFlight.delete(key));
    inFlight.set(key, pending);
  }
  return signal ? raceAbort(pending, signal) : pending;
}

function raceAbort<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  if (signal.aborted) return Promise.reject(new DOMException('Cancelado', 'AbortError'));
  return new Promise((resolve, reject) => {
    const onAbort = () => reject(new DOMException('Cancelado', 'AbortError'));
    signal.addEventListener('abort', onAbort, { once: true });
    promise.then(resolve, reject).finally(() => signal.removeEventListener('abort', onAbort));
  });
}

async function loadDocument<T>(
  url: string,
  ttl: { fresh: number; stale: number },
  parse: (raw: string) => T,
  { forceRefresh = false, cachedOnly = false, signal }: LoadOptions = {},
): Promise<CatalogLoad<T>> {
  const now = Date.now();
  const cached = await documentCache.read(url);
  const age = cached ? now - cached.savedAt : Number.POSITIVE_INFINITY;
  // Solo se parsea la copia local cuando hace falta: está fresca o la red falla.
  let parsedCache: { value: T } | null | undefined;
  const cachedValue = (): T | null => {
    if (parsedCache === undefined) {
      try {
        parsedCache = cached ? { value: parse(cached.raw) } : null;
      } catch {
        parsedCache = null;
      }
    }
    return parsedCache ? parsedCache.value : null;
  };

  if (cachedOnly) {
    const value = cachedValue();
    if (value === null) throw new NotCachedError('Sin copia local');
    return { value, isStale: age > ttl.fresh };
  }
  if (!forceRefresh && age <= ttl.fresh) {
    const value = cachedValue();
    if (value !== null) return { value, isStale: false };
  }

  try {
    // Con copia local se revalida: el navegador manda su ETag y un 304 no descarga nada.
    const raw = await fetchShared(url, cached != null || forceRefresh, signal);
    if (cached && raw === cached.raw) {
      void documentCache.touch(url, now);
      return { value: cachedValue() ?? parse(raw), isStale: false };
    }
    const value = parse(raw);
    // La persistencia es opcional: una descarga válida se muestra aunque no se guarde.
    void documentCache.write(url, raw, now).catch(() => {});
    return { value, isStale: false };
  } catch (error) {
    if (isAbort(error)) throw error;
    const fallback = age <= ttl.stale ? cachedValue() : null;
    if (fallback !== null) return { value: fallback, isStale: true };
    throw error;
  }
}

async function fetchParsed<T>(url: string, parse: (raw: string) => T, signal?: AbortSignal): Promise<T> {
  return parse(await fetchShared(url, false, signal));
}

const enc = encodeURIComponent;

export const repository = {
  loadPortada(url: string, options?: LoadOptions): Promise<CatalogLoad<HomeFeed>> {
    return loadDocument(url, HOME, parser.parseHome, options);
  },

  loadModule(row: HomeRow, options?: LoadOptions): Promise<CatalogLoad<CatalogModule>> {
    if (!row.contentUrl) return Promise.reject(new Error('Este módulo no tiene una fuente remota'));
    return loadDocument(row.contentUrl, MODULE, (raw) => parser.parseModule(raw, row.title), options);
  },

  /** Menú de Explorar, con las imágenes de la configuración de TV como reserva. */
  async loadExplore(options?: LoadOptions): Promise<CatalogLoad<ExploreGroup[]>> {
    const groups = await loadDocument(RtveUrls.REMOTE_CONFIG, CONFIG, parser.parseExplore, options);
    let fallbacks = parser.NO_EXPLORE_IMAGES;
    try {
      fallbacks = (await loadDocument(RtveUrls.REMOTE_CONFIG_TV, CONFIG, parser.parseExploreImages, { signal: options?.signal })).value;
    } catch (error) {
      if (isAbort(error)) throw error;
    }
    return {
      ...groups,
      value: groups.value.map((group) => ({
        ...group,
        categories: group.categories.map((category) => {
          const fallback = parser.exploreImageFor(fallbacks, category);
          return { ...category, fallbackImageUrl: fallback && fallback !== category.imageUrl ? fallback : null };
        }),
      })),
    };
  },

  loadQuickFilters(signal?: AbortSignal): Promise<CatalogLoad<QuickFilter[]>> {
    return loadDocument(RtveUrls.QUICK_SEARCHES, CONFIG, parser.parseQuickFilters, { signal });
  },

  loadQuickFilterItems(filter: QuickFilter, signal?: AbortSignal): Promise<CatalogLoad<CatalogModule>> {
    return loadDocument(filter.contentUrl, MODULE, (raw) => parser.parseModule(raw, filter.title), { signal });
  },

  /** Las búsquedas no se guardan: solo interesa el resultado del momento. */
  search(query: string, signal?: AbortSignal): Promise<SearchResults> {
    return fetchParsed(`${RtveUrls.SEARCH}?search=${enc(query)}&context=tve&tipology=video&type=completo`, parser.parseSearch, signal);
  },

  searchPage(relativeQuery: string, page: number, signal?: AbortSignal): Promise<CatalogPage> {
    return fetchParsed(`${RtveUrls.SEARCH_PAGES}${relativeQuery}&page=${page}`, parser.parseVideoPage, signal);
  },

  /** "Similares" según RTVE, sin el propio item ni contenido de pago. */
  loadRelated(kind: RelatedKind, id: string, signal?: AbortSignal): Promise<CatalogLoad<CatalogItem[]>> {
    return loadDocument(
      `${RtveUrls.RECOMMENDATIONS}/${kind}?source=apps&item=/${RELATED_PREFIX[kind]}/${enc(id)}`,
      PROGRAM,
      (raw) => parser.parseVideoPage(raw).items.filter((it) => it.id !== id && !it.paid).slice(0, MAX_RELATED),
      { signal },
    );
  },

  loadProgram(programId: string, options?: LoadOptions): Promise<CatalogLoad<ProgramDetail>> {
    return loadDocument(`${RtveUrls.PROGRAMS}/${enc(programId)}.json`, PROGRAM, parser.parseProgram, options);
  },

  loadVideo(videoId: string, options?: LoadOptions): Promise<CatalogLoad<VideoDetail>> {
    return loadDocument(`${RtveUrls.VIDEOS}/${enc(videoId)}.json`, PROGRAM, parser.parseVideo, options);
  },

  /** Siguiente episodio según RTVE, o `null` si no hay. */
  loadNextVideo(videoId: string, signal?: AbortSignal): Promise<CatalogItem | null> {
    return fetchParsed(`${RtveUrls.VIDEOS}/${enc(videoId)}/next.json`, (raw) => parser.parseVideoPage(raw).items[0] ?? null, signal);
  },

  /** Miniaturas para la barra de progreso, o `null` si el vídeo no las tiene. */
  async loadPreviewSprite(videoId: string, signal?: AbortSignal): Promise<PreviewSprite | null> {
    const info = await fetchParsed(`${RtveUrls.PREVIEWS}/sprite?idasset=${enc(videoId)}`, parser.parseSpriteInfo, signal);
    if (!info) return null;
    return fetchParsed(info.vtt, (raw) => parser.parseSpriteVtt(raw, info.sprite), signal);
  },

  /** Episodios completos (o todo, sin [completeOnly]) de un programa o una temporada. */
  loadProgramVideos(
    programId: string,
    { seasonId = null, page = 1, completeOnly = true, order = 'NEWEST_FIRST', signal }:
      { seasonId?: string | null; page?: number; completeOnly?: boolean; order?: EpisodeOrder; signal?: AbortSignal } = {},
  ): Promise<CatalogLoad<CatalogPage>> {
    let url = `${RtveUrls.PROGRAMS}/${enc(programId)}`;
    if (seasonId) url += `/temporadas/${enc(seasonId)}`;
    url += `/videos.json?page=${page}`;
    if (completeOnly) url += `&type=${RtveTypes.COMPLETE}`;
    url += `&order=${enc(orderApiValue(order))}`;
    return loadDocument(url, PROGRAM, parser.parseVideoPage, { signal });
  },

  /** Fragmentos y avances de un programa, separados de sus episodios completos. */
  loadProgramExtras(programId: string, page = 1, signal?: AbortSignal): Promise<CatalogLoad<CatalogPage>> {
    const url = `${RtveUrls.PROGRAMS}/${enc(programId)}/videos.json?page=${page}&type=${RtveTypes.FRAGMENT},${RtveTypes.PREVIEW}&order=${enc(orderApiValue('NEWEST_FIRST'))}`;
    return loadDocument(url, PROGRAM, parser.parseVideoPage, { signal });
  },

  /** URL de licencia Widevine: lleva un token temporal y nunca se guarda ni se registra. */
  async widevineLicenseUrl(tokenUrl: string, signal?: AbortSignal): Promise<string> {
    const root = JSON.parse(await getText(tokenUrl, { signal, noStore: true }));
    const url = typeof root?.widevineURL === 'string' ? root.widevineURL.trim() : '';
    if (!url) throw new Error('La respuesta no incluye licencia Widevine');
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' || !parsed.hostname) throw new Error('URL de licencia no admisible');
    return url;
  },

  /** Convierte un enlace de RTVE en el item al que apunta (DeepLinkResolver.kt). */
  async resolveLink(url: string, signal?: AbortSignal): Promise<CatalogItem | null> {
    const link = parseDeepLink(url);
    if (!link) return null;
    switch (link.type) {
      case 'video':
        return (await this.loadVideo(link.id, { signal })).value.item;
      case 'program': {
        const detail = (await this.loadProgram(link.id, { signal })).value;
        return referenceItem(detail.id, 'PROGRAM', detail.title, detail.imageUrl);
      }
      case 'live':
        return (await this.loadModule(liveRow(`${RtveUrls.LIVES}/${link.assetId}.json`), { signal })).value.items
          .find((it) => it.kind === 'LIVE') ?? null;
      case 'programPermalink': {
        const marker = `/play/videos/${link.permalink}/`;
        return (await this.search(link.permalink.replaceAll('-', ' '), signal)).programs
          .find((it) => it.webUrl?.endsWith(marker)) ?? null;
      }
      case 'livePermalink':
        return (await this.loadModule(liveRow(`${RtveUrls.LIVES}/agr-directos/1/directos.json`), { signal })).value.items
          .find((it) => it.webUrl?.replace(/\/+$/, '').endsWith(`/directo/${link.permalink}`)) ?? null;
    }
  },
};
