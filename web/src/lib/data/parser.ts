import { isCollection, programStructure } from '../domain/classification';
import { seasonsInDisplayOrder } from '../domain/episodeOrder';
import { isAllowedUrl, sanitizeUrl } from '../domain/hostPolicy';
import type {
  CatalogItem,
  CatalogModule,
  CatalogPage,
  ContentKind,
  ExploreCategory,
  ExploreGroup,
  HomeFeed,
  HomeLink,
  HomeRow,
  LinkKind,
  LiveInfo,
  PreviewSprite,
  ProgramDetail,
  ProgramSeason,
  QuickFilter,
  RowLayout,
  SearchResults,
  SpriteCue,
  VideoDetail,
} from '../domain/models';

/*
 * Lectura de los feeds de RTVE (RtveJsonParser.kt). Los JSON llegan con claves
 * opcionales, tipos mezclados (números como texto y al revés) y nombres
 * alternativos según el endpoint: estas funciones lo absorben todo.
 */

type Json = Record<string, unknown>;

const LIVE_EDITORIAL_TYPES = new Set(['broadcast', 'peticion', 'directo']);
const NON_CATALOG_ROWS = new Set(['noticias', 'parrilla']);
const YEAR = /\b(?:19|20)\d{2}\b/;
const CLAN_PACKAGE = 'com.rtve.clan';
const IMAGE_SERVICE = 'https://img.rtve.es';
const LANDSCAPE_WIDTH = 960;
const POSTER_WIDTH = 480;
const SQUARE_WIDTH = 480;
const BACKDROP_WIDTH = 1600;

// ---- Acceso tolerante a los JSON ----

const isObj = (v: unknown): v is Json => typeof v === 'object' && v !== null && !Array.isArray(v);
const obj = (o: Json | null | undefined, key: string): Json | null => (o && isObj(o[key]) ? (o[key] as Json) : null);
const arr = (o: Json | null | undefined, key: string): unknown[] | null => (o && Array.isArray(o[key]) ? (o[key] as unknown[]) : null);
const objects = (list: unknown[] | null | undefined): Json[] => (list ?? []).filter(isObj);

/** Primer valor primitivo no vacío de [keys], como texto. */
function text(o: Json | null | undefined, ...keys: string[]): string | null {
  if (!o) return null;
  for (const key of keys) {
    const v = o[key];
    if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
      const s = String(v).trim();
      if (s !== '') return s;
    }
  }
  return null;
}

function int(o: Json | null | undefined, ...keys: string[]): number | null {
  if (!o) return null;
  for (const key of keys) {
    const v = o[key];
    const n = typeof v === 'number' ? v : typeof v === 'string' && /^-?\d+$/.test(v.trim()) ? Number(v) : NaN;
    if (Number.isInteger(n)) return n;
  }
  return null;
}

function flag(o: Json | null | undefined, key: string): boolean | null {
  const v = o?.[key];
  if (typeof v === 'boolean') return v;
  if (v === 'true') return true;
  if (v === 'false') return false;
  return null;
}

const firstAllowed = (...candidates: (string | null)[]) => {
  for (const c of candidates) {
    const s = sanitizeUrl(c);
    if (s) return s;
  }
  return null;
};

function parse(raw: string): Json {
  const root = JSON.parse(raw);
  if (!isObj(root)) throw new Error('Respuesta inesperada');
  return root;
}

function distinctBy<T>(items: T[], key: (item: T) => unknown): T[] {
  const seen = new Set<unknown>();
  return items.filter((item) => {
    const k = key(item);
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
}

// ---- Portadas y módulos ----

export function parseHome(raw: string): HomeFeed {
  const root = parse(raw);
  const rows = objects(arr(root, 'rows'))
    .map((row): HomeRow | null => {
      const presentation = text(row, 'tipo');
      const isLinks = presentation?.toLowerCase() === 'links';
      const links = isLinks ? homeLinks(row) : [];
      // Una fila `links` sin enlaces no aporta nada.
      if (isLinks && links.length === 0) return null;
      const url = text(row, 'urlContent');
      return {
        id: -1,
        title: editorialTitle(text(row, 'title', 'name') ?? ''),
        order: int(row, 'orden', 'order') ?? Number.MAX_SAFE_INTEGER,
        moduleType: text(row, 'moduleType'),
        presentation,
        // El `urlContent` de una fila `links` es otro documento; la app oficial usa los inline.
        contentUrl: url && !isLinks && isAllowedUrl(url) ? url : null,
        layout: rowLayout(presentation),
        links,
      };
    })
    .filter((row): row is HomeRow => row !== null)
    // Noticias no son reproducibles; la parrilla tiene otra estructura.
    .filter((row) => !NON_CATALOG_ROWS.has(row.presentation?.toLowerCase() ?? ''))
    .map((row, index) => ({ row, index }))
    .sort((a, b) => a.row.order - b.row.order || a.index - b.index)
    .map(({ row }, index) => ({ ...row, id: index }));

  return { id: text(root, 'id'), title: text(root, 'title') ?? 'RTVE Play', rows };
}

/** Muchas filas de portada llegan sin título; la propia colección sí lo trae. */
export function parseModule(raw: string, fallbackTitle: string): CatalogModule {
  const root = parse(raw);
  const pageItems = arr(obj(root, 'page'), 'items') ?? arr(root, 'items') ?? [];
  const single = pageItems.length === 1 && isObj(pageItems[0]) ? (pageItems[0] as Json) : null;
  const collection = single && arr(single, 'collectionItems') ? single : null;
  const items = toCatalogItems(objects(collection ? arr(collection, 'collectionItems') : pageItems));
  const collectionTitle = text(collection, 'title');
  return {
    title: fallbackTitle.trim() ? fallbackTitle : collectionTitle ? editorialTitle(collectionTitle) : fallbackTitle,
    items,
  };
}

export function parseSearch(raw: string): SearchResults {
  const root = parse(raw);
  const block = (key: string) => toCatalogItems(objects(arr(obj(obj(root, key), 'page'), 'items')));
  const videos = obj(root, 'videos');
  const query = text(videos, 'query');
  return {
    programs: block('programs'),
    videos: block('videos'),
    videosPage: int(obj(videos, 'page'), 'number') ?? 1,
    videosTotalPages: int(obj(videos, 'page'), 'totalPages') ?? 1,
    // Ruta relativa al buscador; si cambiara de host o de forma, no se pagina.
    videosQuery: query && query.startsWith('/') && !query.startsWith('//') ? query : null,
  };
}

export function parseQuickFilters(raw: string): QuickFilter[] {
  return objects(arr(parse(raw), 'items')).flatMap((item) => {
    const url = text(item, 'nav-pag_url');
    const title = text(item, 'nav-pag_name');
    return url && title && isAllowedUrl(url) ? [{ title, contentUrl: url }] : [];
  });
}

/**
 * Categorías del menú de la app oficial (`menuBloques` de televisión), solo las
 * públicas. Cada `submenu` es un grupo con su propio título editorial.
 */
export function parseExplore(raw: string): ExploreGroup[] {
  const root = parse(raw);
  const television = obj(root, 'television');
  const blocks = objects(arr(television, 'menuBloques'));
  // "CLAN" es un enlace a la app de Clan; su portada es la infantil de la propia configuración.
  const clanPortada = sanitizeUrl(text(obj(obj(television, 'secciones'), 'portadaInfantil'), 'urlContent'));
  const groups = blocks.flatMap((block) => exploreGroups(flag(block, 'infantil') === true, arr(block, 'menuItems'), clanPortada));
  // El grupo de canales es el submenú cuyas entradas traen logo ("CANALES").
  const channelGroups = new Set(
    blocks
      .flatMap((b) => objects(arr(b, 'menuItems')))
      .filter((menu) => text(menu, 'tipo') === 'submenu' && objects(arr(menu, 'menuItems')).some((it) => text(it, 'logo') != null))
      .map((menu) => text(menu, 'title'))
      .filter((t): t is string => t != null)
      .map(sentenceCase),
  );
  return withChannelsTogether(groups, channelGroups);
}

/**
 * El menú oficial repite los canales (Clan, TDP, La 2 Cat...). Cada canal sale solo
 * en su grupo; fuera de él se quita lo que lleva a la misma portada o tiene el mismo
 * nombre. Si la copia del canal no trae fondo, conserva el de la que se quita.
 */
function withChannelsTogether(groups: ExploreGroup[], channelGroups: Set<string>): ExploreGroup[] {
  const inChannels = (g: ExploreGroup) => g.title != null && channelGroups.has(g.title);
  const channels = groups.filter(inChannels).flatMap((g) => g.categories);
  if (channels.length === 0) return groups;
  const urls = new Set(channels.map((c) => c.contentUrl));
  const names = new Set(channels.map((c) => c.title.toLowerCase().trim()));
  const isChannel = (c: ExploreCategory) => urls.has(c.contentUrl) || names.has(c.title.toLowerCase().trim());
  const repeatedImages = groups
    .filter((g) => !inChannels(g))
    .flatMap((g) => g.categories)
    .filter((c) => isChannel(c) && c.imageUrl != null);
  return groups.flatMap((group) => {
    if (inChannels(group)) {
      return [{
        ...group,
        categories: group.categories.map((channel) => ({
          ...channel,
          imageUrl: channel.imageUrl ?? repeatedImages.find(
            (it) => it.contentUrl === channel.contentUrl || it.title.toLowerCase().trim() === channel.title.toLowerCase().trim(),
          )?.imageUrl ?? null,
        })),
      }];
    }
    const categories = group.categories.filter((c) => !isChannel(c));
    return categories.length ? [{ ...group, categories }] : [];
  });
}

/** Imagen de cada portada del menú, por su URL; sirve de reserva para la otra configuración. */
export function parseExploreImages(raw: string): Map<string, string> {
  const map = new Map<string, string>();
  for (const c of parseExplore(raw).flatMap((g) => g.categories)) if (c.imageUrl) map.set(c.contentUrl, c.imageUrl);
  return map;
}

function exploreGroups(isKids: boolean, items: unknown[] | null, clanPortada: string | null): ExploreGroup[] {
  // La app oficial respeta el orden del JSON, no `orden`.
  const list = objects(items);
  const direct = exploreCategories(list, clanPortada);
  const submenus = list
    .filter((it) => text(it, 'tipo') === 'submenu' && flag(it, 'subscriptor') !== true)
    .flatMap((submenu): ExploreGroup[] => {
      const title = text(submenu, 'title');
      if (!title) return [];
      // Dentro de un submenú el enlace a la app de Clan sobra: Clan ya tiene su bloque.
      const categories = exploreCategories(objects(arr(submenu, 'menuItems')), null);
      return categories.length ? [{ title: sentenceCase(title), categories, isKids: false }] : [];
    });
  return [...(direct.length ? [{ title: null, categories: direct, isKids }] : []), ...submenus];
}

function exploreCategories(items: Json[], clanPortada: string | null): ExploreCategory[] {
  const categories = items
    .filter((it) => flag(it, 'subscriptor') !== true)
    .flatMap((item): ExploreCategory[] => {
      const title = text(item, 'title');
      if (!title) return [];
      const tipo = text(item, 'tipo');
      let contentUrl: string | null = null;
      if (tipo === 'portada' || tipo === 'portadaCanalTematico') contentUrl = sanitizeUrl(text(item, 'urlContent'));
      else if (tipo === 'intentApp' && text(item, 'appAndroid') === CLAN_PACKAGE) contentUrl = clanPortada;
      if (!contentUrl) return [];
      return [{
        title,
        imageUrl: sanitizeUrl(text(item, 'imgBackground')),
        contentUrl,
        isLive: tipo === 'portadaCanalTematico',
        fallbackImageUrl: null,
        nowPlaying: null,
      }];
    });
  return distinctBy(categories, (c) => c.contentUrl);
}

/** Accesos inline de una fila `links`; `enlaceExterno` (web) se omite. */
function homeLinks(row: Json): HomeLink[] {
  const kinds: Record<string, LinkKind> = {
    collection: 'COLLECTION',
    portadaPlay: 'PORTADA',
    programaPlay: 'PROGRAM',
    videoPlay: 'VIDEO',
  };
  const links = objects(arr(row, 'links')).flatMap((link): HomeLink[] => {
    const kind = kinds[text(link, 'tipo') ?? ''];
    const title = text(link, 'title');
    const url = sanitizeUrl(text(link, 'url'));
    if (!kind || !title || !url) return [];
    return [{ title, imageUrl: firstAllowed(text(link, 'imgHorizontal', 'image', 'imgSquare')), url, kind }];
  });
  return distinctBy(links, (l) => l.url);
}

/** Un valor por presentación de RTVE Play. */
export function rowLayout(presentation: string | null | undefined): RowLayout {
  switch (presentation?.toLowerCase()) {
    case 'colecciondestacado': return 'HERO';
    case 'coleccionsuperdestacado': return 'FEATURED';
    case 'coleccionposter':
    case 'videoposter':
    case 'storiesposter':
    case 'programas': return 'POSTER';
    case 'coleccionsuper': return 'POSTER_TALL';
    case 'coleccioncuadrado':
    case 'coleccioncuadradopeq': return 'SQUARE';
    case 'collecciontops':
    case 'colecciontops':
    case 'tops': return 'RANKED';
    default: return 'LANDSCAPE';
  }
}

// ---- Fichas ----

export function parseProgram(raw: string): ProgramDetail {
  const root = parse(raw);
  const program = objects(arr(obj(root, 'page'), 'items'))[0];
  if (!program) throw new Error('El programa no tiene ficha');
  const id = text(program, 'id');
  if (!id) throw new Error('Programa sin id');
  const seasons = objects(arr(program, 'seasons')).flatMap((season): ProgramSeason[] => {
    const seasonId = text(season, 'id');
    if (!seasonId) return [];
    return [{
      id: seasonId,
      title: text(season, 'longTitle', 'shorttitle', 'shortTitle', 'title') ?? seasonId,
      episodeCount: int(season, 'numEpisodes'),
      order: int(season, 'orden'),
    }];
  });
  const detail: ProgramDetail = {
    id,
    title: text(program, 'name', 'title', 'longTitle') ?? '',
    description: text(program, 'description', 'longDescription', 'promoDesc'),
    imageUrl: firstAllowed(text(program, 'imgPortada', 'imgPortada2')) ?? derivedImage('p', id, 'imgPortada', BACKDROP_WIDTH),
    emission: text(program, 'emission'),
    seasons,
    webUrl: sanitizeUrl(text(program, 'htmlUrl')),
    programTypeId: text(program, 'programTypeId', 'programTypeID'),
    programType: text(program, 'programType'),
    inEmission: flag(program, 'outOfEmission') !== true,
    isComplete: flag(program, 'isComplete') === true,
    showFragments: flag(program, 'showFragments') === true,
    year: productionYear(text(program, 'productionDate')),
    ageRating: text(program, 'ageRange'),
    genres: genres(program),
    director: people(program, 'director'),
    cast: peopleList(program, 'casting'),
    originalLanguage: text(program, 'languageOriginal'),
    country: text(program, 'country'),
  };
  return { ...detail, seasons: seasonsInDisplayOrder(detail) };
}

export function parseVideoPage(raw: string): CatalogPage {
  const page = obj(parse(raw), 'page');
  return {
    items: toCatalogItems(objects(arr(page, 'items'))),
    page: int(page, 'number') ?? 1,
    totalPages: int(page, 'totalPages') ?? 1,
  };
}

export function parseVideo(raw: string): VideoDetail {
  const video = objects(arr(obj(parse(raw), 'page'), 'items'))[0];
  if (!video) throw new Error('El vídeo no tiene ficha');
  const item = toCatalogItem(video);
  if (!item) throw new Error('Vídeo sin id');
  const previews = obj(video, 'previews');
  return {
    item,
    backdropUrl:
      firstAllowed(text(previews, 'horizontal2', 'horizontal'), text(video, 'thumbnail', 'imageSEO')) ??
      derivedImage('v', item.id, 'horizontal2', BACKDROP_WIDTH),
    description: text(video, 'description', 'longDescription', 'shortDescription'),
    promo: text(video, 'promoDesc'),
    programTitle: text(obj(video, 'programInfo'), 'title'),
    year: productionYear(text(video, 'productionDate')),
    ageRating: text(video, 'ageRange'),
    genres: genres(video),
    director: people(video, 'director'),
    cast: peopleList(video, 'casting'),
    originalLanguage: text(video, 'languageOriginal'),
    expirationDate: text(video, 'expirationDate'),
    webUrl: sanitizeUrl(text(video, 'htmlUrl')),
  };
}

// ---- Items ----

/**
 * Una lista de "programa + lastMultimedia" puede ser una lista de programas (cada
 * uno con su último episodio) o una lista de medios que repite el programa en cada
 * item. Se deduce de los datos: si el programa se repite, cada item es su medio.
 */
function toCatalogItems(list: Json[]): CatalogItem[] {
  const occurrences = new Map<string | null, number>();
  for (const it of list) {
    if (obj(it, 'lastMultimedia')) {
      const key = text(it, 'id', 'uid');
      occurrences.set(key, (occurrences.get(key) ?? 0) + 1);
    }
  }
  const items = list
    .map((it) => toCatalogItem(it, (occurrences.get(text(it, 'id', 'uid')) ?? 0) > 1))
    // Noticias, enlaces o tipos desconocidos no se pueden abrir ni reproducir.
    .filter((it): it is CatalogItem => it !== null && it.kind !== 'UNKNOWN');
  // Los feeds repiten items.
  return distinctBy(items, (it) => it.id);
}

function toCatalogItem(item: Json, mediaFirst = false): CatalogItem | null {
  const nestedMedia = obj(item, 'lastMultimedia');
  // Sin id propio el item no es un programa: es una envoltura del medio que trae dentro.
  const bareMedia = nestedMedia != null && text(item, 'id', 'uid') == null;
  if (nestedMedia && (mediaFirst || bareMedia || isContainer(item))) {
    const mediaItem = toCatalogItem(nestedMedia);
    if (!mediaItem) return null;
    return {
      ...mediaItem,
      subtitle: mediaItem.subtitle ?? text(item, 'name', 'title'),
      programId: mediaItem.programId ?? text(item, 'id'),
    };
  }
  const media = nestedMedia ?? item;
  const id = text(item, 'id', 'uid') ?? text(media, 'id', 'uid');
  if (!id) return null;
  const title =
    text(item, 'title', 'name', 'titulo', 'longTitle', 'shortTitle', 'metaTitle') ??
    text(media, 'title', 'name', 'longTitle') ??
    fallbackTitle(item);
  if (!title) return null;
  const mediaTitleRaw = text(media, 'title', 'longTitle');
  const mediaTitle = mediaTitleRaw !== title ? mediaTitleRaw : null;
  const contentType = text(media, 'contentType') ?? text(item, 'contentType');
  const editorialType = text(media, 'tipo') ?? text(item, 'tipo');
  const type = obj(media, 'type') ?? obj(item, 'type');
  const subtype = obj(media, 'subType') ?? obj(item, 'subType');
  const typeValue = text(media, 'type') ?? text(item, 'type');
  const subtypeValue = text(media, 'subType') ?? text(item, 'subType');
  const programInfo = obj(media, 'programInfo') ?? obj(item, 'programInfo');
  const assetId = text(media, 'idAsset') ?? text(item, 'idAsset');
  const kind = contentKind(contentType, editorialType, nestedMedia != null, assetId);
  const live = kind === 'LIVE' ? liveInfo(item) : null;
  let subtitle: string | null = null;
  if (mediaTitle != null) subtitle = mediaTitle;
  else if (kind === 'VIDEO') subtitle = text(obj(item, 'programInfo'), 'title');
  else if (kind === 'LIVE') subtitle = live?.category ?? null;
  const digits = (v: string | null) => v != null && /^\d+$/.test(v);
  const episode = int(media, 'episode');

  return {
    id,
    playbackId: nestedMedia ? text(nestedMedia, 'id') : kind === 'PROGRAM' || kind === 'UNKNOWN' ? null : id,
    assetId,
    title,
    subtitle,
    imageUrl: landscapeImage(kind, id, media, item),
    kind,
    posterUrl: posterImage(kind, id, media, item),
    squareUrl: squareImage(kind, id, media, item),
    tallPosterUrl: kind === 'PROGRAM' ? derivedImage('p', id, 'imgCol', POSTER_WIDTH) : null,
    programId: kind === 'PROGRAM' ? id : text(programInfo, 'id'),
    seasonId: text(media, 'temporadaId') ?? text(item, 'temporadaId'),
    mediaTypeId: text(type, 'id') ?? (digits(typeValue) ? typeValue : null),
    mediaTypeName: text(type, 'name') ?? (typeValue != null && !digits(typeValue) ? typeValue : null),
    mediaSubtypeName: text(subtype, 'name') ?? (subtypeValue != null && !digits(subtypeValue) ? subtypeValue : null),
    mediaSubtypeId: text(subtype, 'id') ?? (digits(subtypeValue) ? subtypeValue : null),
    programShowsFragments: flag(programInfo, 'showFragments'),
    programTypeId: text(programInfo, 'programTypeId', 'programTypeID'),
    programType: text(programInfo, 'programType'),
    allowedInCountry: flag(media, 'allowedInCountry') ?? flag(item, 'allowedInCountry'),
    loginRequired: flag(media, 'requireLogged') ?? flag(item, 'requireLogged') ?? false,
    paid: flag(media, 'paidContent') ?? flag(item, 'paidContent') ?? false,
    drm: flag(media, 'hasDRM') ?? flag(item, 'hasDRM') ?? false,
    durationMs: int(media, 'duration'),
    publicationDate: text(media, 'publicationDate', 'dateOfEmission'),
    episode: episode != null && episode > 0 ? episode : null,
    seasonTitle: text(media, 'temporada', 'temporadaShortTitle'),
    live,
    webUrl: sanitizeUrl(text(item, 'htmlUrl') ?? text(media, 'htmlUrl')),
  };
}

function liveInfo(item: Json): LiveInfo {
  const duration = int(item, 'duracion');
  const category = text(item, 'antetitulo');
  return {
    isOnAir: flag(item, 'live') ?? true,
    startsAtMillis: parseMadridTime(text(item, 'inicio')),
    durationMinutes: duration != null && duration > 0 ? duration : null,
    progressPercent: int(item, 'porcentaje'),
    channelLogoUrl: sanitizeUrl(text(item, 'logo')),
    category: category ? titleCase(category) : null,
  };
}

/** El feed escribe las horas en local de Madrid, sin zona (`dd-MM-yyyy HH:mm:ss`). */
export function parseMadridTime(value: string | null): number | null {
  const m = value && /^(\d{2})-(\d{2})-(\d{4}) (\d{2}):(\d{2}):(\d{2})$/.exec(value.trim());
  if (!m) return null;
  const [, dd, mm, yyyy, hh, mi, ss] = m.map(Number);
  const asUtc = Date.UTC(yyyy, mm - 1, dd, hh, mi, ss);
  // Desfase de Madrid en ese instante; dos pasadas para acertar en los cambios de hora.
  let guess = asUtc - madridOffset(asUtc);
  guess = asUtc - madridOffset(guess);
  return guess;
}

const madridParts = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Europe/Madrid',
  hourCycle: 'h23',
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
});

function madridOffset(instant: number): number {
  const p: Record<string, number> = {};
  for (const part of madridParts.formatToParts(new Date(instant))) if (part.type !== 'literal') p[part.type] = Number(part.value);
  return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - Math.floor(instant / 1000) * 1000;
}

/** "Portada En Play > Destacados" -> "Destacados". */
function editorialTitle(value: string): string {
  const i = value.lastIndexOf(' > ');
  const last = (i >= 0 ? value.slice(i + 3) : value).trim();
  return last || value;
}

/** "OTRAS TEMÁTICAS" -> "Otras temáticas". */
function sentenceCase(value: string): string {
  if (value !== value.toUpperCase()) return value;
  const lower = value.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** "LA VUELTA 2026" -> "La Vuelta 2026". */
function titleCase(value: string): string {
  if (value !== value.toUpperCase()) return value;
  return value.toLowerCase().split(' ').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

/** Respuesta de `tiivii-previews/api/sprite`: URLs del sprite y de su VTT. */
export function parseSpriteInfo(raw: string): { sprite: string; vtt: string } | null {
  const root = parse(raw);
  if (text(root, 'state') !== 'AVAILABLE') return null;
  const sprite = sanitizeUrl(text(root, 'sprite_url'));
  const vtt = sanitizeUrl(text(root, 'vtt_url'));
  return sprite && vtt ? { sprite, vtt } : null;
}

/** VTT de regiones: `00:00:10 --> 00:00:20` y una URL con `#xywh=x,y,w,h`. */
export function parseSpriteVtt(vtt: string, spriteUrl: string): PreviewSprite {
  const cues: SpriteCue[] = [];
  const lines = vtt.split(/\r?\n/);
  let index = 0;
  while (index < lines.length) {
    const line = lines[index].trim();
    const arrow = line.indexOf('-->');
    if (arrow > 0) {
      const start = parseVttTime(line.slice(0, arrow).trim());
      const end = parseVttTime(line.slice(arrow + 3).trim().split(/\s+/)[0]);
      const regionText = lines[index + 1]?.split('#xywh=')[1];
      const region = regionText?.split(',').map((v) => Number.parseInt(v.trim(), 10)).filter((n) => !Number.isNaN(n));
      if (start != null && end != null && region?.length === 4) {
        cues.push({ startMs: start, endMs: end, x: region[0], y: region[1], width: region[2], height: region[3] });
      }
      index += 2;
    } else {
      index++;
    }
  }
  return { imageUrl: spriteUrl, cues };
}

function parseVttTime(value: string): number | null {
  const parts = value.split(':');
  if (parts.length < 2 || parts.length > 3) return null;
  const seconds = Number(parts[parts.length - 1]);
  const minutes = Number(parts[parts.length - 2]);
  const hours = parts.length === 3 ? Number(parts[0]) : 0;
  if ([seconds, minutes, hours].some(Number.isNaN)) return null;
  return (hours * 3600 + minutes * 60) * 1000 + Math.floor(seconds * 1000);
}

const isContainer = (program: Json) =>
  isCollection(programStructure(text(program, 'programTypeId', 'programTypeID'), text(program, 'programType')));

function contentKind(contentType: string | null, editorialType: string | null, isProgramWithMedia: boolean, assetId: string | null): ContentKind {
  if (editorialType && LIVE_EDITORIAL_TYPES.has(editorialType.toLowerCase())) return 'LIVE';
  switch (contentType?.toLowerCase()) {
    case 'video': return isProgramWithMedia ? 'PROGRAM' : 'VIDEO';
    case 'program':
    case 'programa': return 'PROGRAM';
    case 'directo':
    case 'live':
    case 'broadcast': return 'LIVE';
    // Sin tipo declarado, un idAsset es la única pista de que se trata de un directo.
    case undefined: return assetId != null ? 'LIVE' : 'UNKNOWN';
    default: return 'UNKNOWN';
  }
}

/*
 * Imágenes por proporción: `imgPortada`, `thumb` y `previews.horizontal*` son 16:9;
 * `imgPoster`, `thumb_vertical` y `previews.vertical*` 2:3; `imgBackground`,
 * `thumb_square` y `previews.square*` 1:1; `imgCol` 1:2. Para vídeos y programas
 * el servicio por id devuelve siempre la variante pedida y admite `w=`.
 */
function landscapeImage(kind: ContentKind, id: string, media: Json, item: Json): string | null {
  if (kind === 'VIDEO') return derivedImage('v', id, 'horizontal2', LANDSCAPE_WIDTH);
  if (kind === 'PROGRAM') return derivedImage('p', id, 'imgPortada', LANDSCAPE_WIDTH);
  return firstAllowed(
    text(item, 'thumb', 'imagen', 'imgPortada', 'imgPortada2', 'thumbnail'),
    text(obj(media, 'previews'), 'horizontal2', 'horizontal'),
    text(item, 'imageSEO', 'imgBackground'),
  );
}

function posterImage(kind: ContentKind, id: string, media: Json, item: Json): string | null {
  if (kind === 'VIDEO') return derivedImage('v', id, 'vertical', POSTER_WIDTH);
  if (kind === 'PROGRAM') return derivedImage('p', id, 'imgPoster', POSTER_WIDTH);
  return firstAllowed(text(item, 'thumb_vertical', 'imgPoster', 'imgPoster2'), text(obj(media, 'previews'), 'vertical2', 'vertical'));
}

function squareImage(kind: ContentKind, id: string, media: Json, item: Json): string | null {
  if (kind === 'PROGRAM') return derivedImage('p', id, 'imgBackground', SQUARE_WIDTH);
  if (kind === 'VIDEO') return null;
  return firstAllowed(text(item, 'thumb_square', 'imgBackground'), text(obj(media, 'previews'), 'square', 'square2'));
}

function derivedImage(type: string, id: string, variant: string, width: number): string {
  const safeId = encodeURIComponent(id);
  if (type === 'p') return `${IMAGE_SERVICE}/p/${safeId}?imgProgApi=${variant}&w=${width}`;
  if (type === 'v') return `${IMAGE_SERVICE}/v/${safeId}/${variant}?w=${width}`;
  return `${IMAGE_SERVICE}/${type}/${safeId}?w=${width}`;
}

/**
 * Los canales FAST llegan sin `titulo`; su descripción empieza por el nombre
 * ("Canal RTVE La Promesa: ...") y, si no, queda el permalink ("play-promesa").
 */
function fallbackTitle(item: Json): string | null {
  const description = text(item, 'descripcion', 'description')?.split(':')[0].split('.')[0].trim();
  if (description && description.length >= 3 && description.length <= 60) return description;
  const permalink = text(item, 'permalink');
  return permalink ? permalink.split('-').map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ') : null;
}

function genres(o: Json): string[] {
  const names = objects(arr(o, 'generos'))
    .map((g) => text(g, 'subGeneroInf', 'generoInf'))
    .filter((g): g is string => g != null);
  return [...new Set(names)];
}

function peopleList(o: Json, key: string): string[] {
  return (text(o, key) ?? '').split('|').map((s) => s.trim()).filter(Boolean);
}

function people(o: Json, key: string): string | null {
  return peopleList(o, key).join(', ') || null;
}

const productionYear = (value: string | null) => (value ? YEAR.exec(value)?.[0] ?? null : null);
