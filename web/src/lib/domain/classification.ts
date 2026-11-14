import type { CatalogItem, ProgramDetail } from './models';

/*
 * Clasificación editorial de RTVE, en un solo sitio (MediaClassification.kt).
 * Tres ejes independientes: la forma del medio (completo o extra), su tema
 * (película, documental u otro) y la estructura del programa (serie o contenedor).
 */
export const RtveTypes = {
  COMPLETE: '39816',
  FRAGMENT: '39978',
  PREVIEW: '39980',
  MOVIE: '130734',
  DOCUMENTARY: '130735',
  MOVIE_CONTAINER: '137310',
  CONTAINER_PREFIX: 'Contenedor ',
} as const;

export type MediaForm = 'COMPLETE' | 'EXTRA' | 'UNKNOWN';
export type MediaSubject = 'MOVIE' | 'DOCUMENTARY' | 'OTHER';
export type ProgramStructure = 'EPISODIC' | 'MOVIES' | 'DOCUMENTARIES' | 'VIDEOS';

/** Un contenedor se recorre como catálogo: cada pieza tiene su propia ficha. */
export const isCollection = (structure: ProgramStructure) => structure !== 'EPISODIC';

/** La única regla de estructura; la usan el parser, las fichas y el reproductor. */
export function programStructure(programTypeId: string | null | undefined, programType: string | null | undefined): ProgramStructure {
  if (programTypeId === RtveTypes.MOVIE_CONTAINER || programType?.toLowerCase() === 'contenedor películas') return 'MOVIES';
  if (!programType?.toLowerCase().startsWith(RtveTypes.CONTAINER_PREFIX.toLowerCase())) return 'EPISODIC';
  if (programType.toLowerCase().includes('documental')) return 'DOCUMENTARIES';
  return 'VIDEOS';
}

export const detailStructure = (detail: ProgramDetail) => programStructure(detail.programTypeId, detail.programType);

/** Estructura del programa al que pertenece un medio (`programInfo`). */
export const parentStructure = (item: CatalogItem) => programStructure(item.programTypeId, item.programType);

export function mediaForm(item: CatalogItem): MediaForm {
  switch (item.mediaTypeId) {
    case RtveTypes.COMPLETE: return 'COMPLETE';
    case RtveTypes.FRAGMENT:
    case RtveTypes.PREVIEW: return 'EXTRA';
  }
  switch (item.mediaTypeName?.toLowerCase()) {
    case 'completo': return 'COMPLETE';
    case 'fragmento':
    case 'avance': return 'EXTRA';
    default: return 'UNKNOWN';
  }
}

export function mediaSubject(item: CatalogItem): MediaSubject {
  switch (item.mediaSubtypeId) {
    case RtveTypes.MOVIE: return 'MOVIE';
    case RtveTypes.DOCUMENTARY: return 'DOCUMENTARY';
  }
  switch (item.mediaSubtypeName?.toLowerCase()) {
    case 'película':
    case 'pelicula': return 'MOVIE';
    case 'documental': return 'DOCUMENTARY';
    default: return 'OTHER';
  }
}

export const isMovie = (item: CatalogItem) => mediaSubject(item) === 'MOVIE';

/** Una película completa tiene ficha propia; su tráiler sigue siendo un extra. */
export const isFullMovie = (item: CatalogItem) => isMovie(item) && mediaForm(item) !== 'EXTRA';

/** Es un capítulo de un programa que se recorre por capítulos. */
export const belongsToEpisodicProgram = (item: CatalogItem) =>
  item.programId != null && !isFullMovie(item) && parentStructure(item) === 'EPISODIC' &&
  (item.seasonId != null || item.episode != null || item.programType != null);

/** Solo los capítulos de una serie encadenan el siguiente. */
export const canHaveNextEpisode = (item: CatalogItem) =>
  item.kind === 'VIDEO' && mediaForm(item) !== 'EXTRA' && !isFullMovie(item) && parentStructure(item) === 'EPISODIC';
