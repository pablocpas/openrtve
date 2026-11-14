import { canHaveNextEpisode, isCollection, type ProgramStructure } from '../domain/classification';
import type { CatalogItem, ExploreGroup, LiveInfo, ProgramDetail, VideoDetail } from '../domain/models';
import type { BlockReason } from '../domain/playback';
import type { LoadError } from '../data/http';

/* Textos de la interfaz (strings.xml y UiText.kt). */

export function errorText(error: LoadError): string {
  switch (error.type) {
    case 'offline': return 'Sin conexión a Internet';
    case 'timeout': return 'RTVE está tardando demasiado en responder';
    case 'http': return `RTVE respondió con un error (HTTP ${error.status})`;
    default: return 'No se pudo cargar el contenido';
  }
}

export function blockedText(reason: BlockReason): string {
  switch (reason) {
    case 'GEO_RESTRICTED': return 'No disponible en tu ubicación';
    case 'LOGIN_REQUIRED': return 'Requiere una sesión autorizada';
    case 'SUBSCRIPTION_REQUIRED': return 'Requiere una suscripción activa';
    case 'NOT_STARTED_YET': return 'Todavía no ha empezado';
    case 'NO_SOURCE': return 'Este elemento todavía no tiene una fuente reproducible';
  }
}

const timeFormat = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });
const dateFormat = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: '2-digit' });
const dayKey = (t: number) => new Date(t).toDateString();

/** "Hoy · 16:10", "Mañana · 16:10" o "15/09 · 16:10", según el reloj. */
export function scheduleLabel(live: LiveInfo, now: number): string | null {
  const start = live.startsAtMillis;
  if (start == null) return null;
  const day =
    dayKey(start) === dayKey(now) ? 'Hoy' : dayKey(start) === dayKey(now + 86_400_000) ? 'Mañana' : dateFormat.format(start);
  return `${day} · ${timeFormat.format(start)}`;
}

/** "52 min", redondeando; nada por debajo del medio minuto. */
export function minutesLabel(durationMs: number | null | undefined): string | null {
  if (durationMs == null) return null;
  const minutes = Math.floor((durationMs + 30_000) / 60_000);
  return minutes > 0 ? `${minutes} min` : null;
}

/** Fecha del feed (`dd-MM-yyyy HH:mm:ss`) como `dd/MM/yyyy`. */
export const feedDateToDisplay = (value: string) => value.slice(0, 10).replaceAll('-', '/');

/**
 * "Temporada 1 · E3 · 12/09/2026 · 52 min", omitiendo lo que falte. El número de
 * episodio no se repite si el título ya lo dice ("Episodio 512").
 */
export function itemMetaLine(item: CatalogItem, episodic = canHaveNextEpisode(item)): string | null {
  const parts = [
    episodic ? item.seasonTitle : null,
    episodic && item.episode != null && !new RegExp(`\\b${item.episode}\\b`).test(item.title) ? `E${item.episode}` : null,
    item.publicationDate ? feedDateToDisplay(item.publicationDate) : null,
    minutesLabel(item.durationMs),
  ].filter(Boolean);
  return parts.length ? parts.join(' · ') : null;
}

/** Géneros que caben en la línea de datos de la cabecera; el resto va a la ficha técnica. */
export const MAX_HEADER_GENRES = 2;

function headerMetaLine(year: string | null, minutes: string | null, ageRating: string | null, genres: string[]): string | null {
  const parts = [year, minutes, ageBadge(ageRating), genres.slice(0, MAX_HEADER_GENRES).join(', ') || null].filter(Boolean);
  return parts.length ? parts.join(' · ') : null;
}

/** "2018 · 108 min · +12 · Comedia, Drama". */
export const videoMetaLine = (d: VideoDetail) => headerMetaLine(d.year, minutesLabel(d.item.durationMs), d.ageRating, d.genres);
export const programMetaLine = (d: ProgramDetail) => headerMetaLine(d.year, null, d.ageRating, d.genres);

/** "No recomendable para menores de 7 años" -> "+7"; "todos los públicos" -> "TP". */
export function ageBadge(ageRange: string | null): string | null {
  const text = ageRange?.toLowerCase();
  if (!text) return null;
  const minimum = /menores de (\d+)/.exec(text)?.[1];
  if (minimum) return `+${minimum}`;
  if (text.includes('todos los públicos')) return 'TP';
  if (text.includes('infancia')) return 'Infantil';
  return null;
}

/**
 * Título de una portada abierta desde el menú: el menú lo da en mayúsculas ("LA
 * REVUELTA") y el feed lo contiene bien escrito ("Portada RTVE Play La Revuelta").
 */
export function portadaTitle(given: string, feedTitle: string): string {
  if (!given.trim()) return feedTitle;
  if (given !== given.toUpperCase()) return given;
  const start = feedTitle.toLowerCase().indexOf(given.toLowerCase());
  if (start >= 0) return feedTitle.slice(start, start + given.length);
  const lower = given.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** Todos los grupos de Explorar llevan cabecera. */
export const exploreHeader = (group: ExploreGroup) => (group.isKids ? 'Infantil' : group.title ?? 'Temáticas');

/** Enlace de una pieza a su colección: "Más en Cine de siempre". */
export const collectionLinkText = (structure: ProgramStructure, collection: string | null) =>
  collection && isCollection(structure) ? `Más en ${collection}` : null;

/** Nombre en español de un código de idioma ("en" -> "Inglés"). */
const languageNames = new Intl.DisplayNames(['es'], { type: 'language' });
const regionNames = new Intl.DisplayNames(['es'], { type: 'region' });

export function languageName(code: string): string {
  try {
    const name = languageNames.of(code) ?? code;
    return name.charAt(0).toUpperCase() + name.slice(1);
  } catch {
    return code;
  }
}

export function countryName(code: string): string {
  try {
    return regionNames.of(code.toUpperCase()) ?? code;
  } catch {
    return code;
  }
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1 << 20) return `${(bytes / (1 << 20)).toFixed(1).replace('.', ',')} MB`;
  if (bytes >= 1 << 10) return `${Math.floor(bytes / 1024)} KB`;
  return `${bytes} B`;
}

/** "1:02:05" o "12:05". */
export function formatClock(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) seconds = 0;
  const s = Math.floor(seconds % 60);
  const m = Math.floor((seconds / 60) % 60);
  const h = Math.floor(seconds / 3600);
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

/** Sinopsis de RTVE: viene con HTML sencillo. Se reduce a párrafos de texto plano, sin marcado ajeno. */
export function htmlToParagraphs(html: string | null | undefined): string[] {
  if (!html) return [];
  const doc = new DOMParser().parseFromString(html.replace(/<br\s*\/?>/gi, '\n').replace(/<\/p>/gi, '\n'), 'text/html');
  return (doc.body.textContent ?? '')
    .split(/\n+/)
    .map((p) => p.replace(/\s+/g, ' ').trim())
    .filter(Boolean);
}
