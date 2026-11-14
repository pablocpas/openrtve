import type { ProgramDetail, ProgramSeason } from './models';

/** Sentido en que se listan los episodios de un programa. */
export type EpisodeOrder = 'NEWEST_FIRST' | 'OLDEST_FIRST';

/** Valor del parámetro `order` de `programas/{id}/videos.json`. */
export const orderApiValue = (order: EpisodeOrder) =>
  order === 'NEWEST_FIRST' ? 'multimedia_date_emission,desc' : 'multimedia_date_emission,asc';

/*
 * Reglas de RTVE Play 8.8.1 (`ProgramaFragmentOrderUtils`): una serie completa se
 * lee del principio; un programa en emisión, del final (salvo sus temporadas
 * pasadas); noticias y eventos siempre del final mientras emiten; conciertos,
 * entrevistas, reportajes y documentales siempre del final; el resto, del principio.
 */
const NEWEST_WHILE_ON_AIR = new Set(['132534', '136522']);
const ALWAYS_NEWEST = new Set(['136613', '136525', '136521', '137650']);

export function episodeOrder(detail: ProgramDetail, isFirstSeason = true): EpisodeOrder {
  const type = detail.programTypeId ?? '';
  if (detail.isComplete) return 'OLDEST_FIRST';
  if (detail.inEmission && NEWEST_WHILE_ON_AIR.has(type)) return 'NEWEST_FIRST';
  if (ALWAYS_NEWEST.has(type)) return 'NEWEST_FIRST';
  if (detail.inEmission && isFirstSeason) return 'NEWEST_FIRST';
  return 'OLDEST_FIRST';
}

/** Orden de las pestañas de temporada. */
export function seasonOrder(detail: ProgramDetail): EpisodeOrder {
  return detail.isComplete || detail.inEmission || ALWAYS_NEWEST.has(detail.programTypeId ?? '') ? 'NEWEST_FIRST' : 'OLDEST_FIRST';
}

/** Temporadas con episodios, en el orden de RTVE Play. */
export function seasonsInDisplayOrder(detail: ProgramDetail): ProgramSeason[] {
  const withEpisodes = detail.seasons.filter((s) => s.episodeCount == null || s.episodeCount > 0);
  const newest = seasonOrder(detail) === 'NEWEST_FIRST';
  return [...withEpisodes].sort((a, b) =>
    newest
      ? (b.order ?? Number.MIN_SAFE_INTEGER) - (a.order ?? Number.MIN_SAFE_INTEGER)
      : (a.order ?? Number.MAX_SAFE_INTEGER) - (b.order ?? Number.MAX_SAFE_INTEGER),
  );
}

/** Si [seasonId] es la primera pestaña; sin temporada seleccionada cuenta como tal. */
export const isFirstSeason = (detail: ProgramDetail, seasonId: string | null) =>
  seasonId == null || detail.seasons[0]?.id === seasonId;
