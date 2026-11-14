import { isUpcomingAt, type CatalogItem } from './models';

export type BlockReason = 'GEO_RESTRICTED' | 'LOGIN_REQUIRED' | 'SUBSCRIPTION_REQUIRED' | 'NO_SOURCE' | 'NOT_STARTED_YET';

export const MIME_HLS = 'application/x-mpegURL';
export const MIME_DASH = 'application/dash+xml';

const ZTNR = 'https://ztnr.rtve.es/ztnr';
const API = 'https://api.rtve.es/api';

export interface PlaybackReady {
  type: 'ready';
  uri: string;
  mimeType: string;
  title: string;
  /** Dónde pedir la licencia Widevine; el token es temporal y nunca se guarda. */
  drmTokenUrl: string | null;
  /** Variante sin protección para navegadores sin Widevine. */
  fallbackUri: string | null;
  /** Clave del item en "Seguir viendo"; `null` en directos. */
  historyKey: string | null;
  /** ID del vídeo bajo demanda: previews de la barra y siguiente episodio. */
  videoId: string | null;
}

export type PlaybackDecision = PlaybackReady | { type: 'blocked'; reason: BlockReason };

/**
 * Qué se reproduce y cómo (PlaybackResolver.kt). `requireLogged` es una puerta del
 * cliente móvil oficial: los streams se sirven sin credenciales, así que se ignora,
 * como hace el cliente de TV.
 */
export function resolvePlayback(item: CatalogItem, now = Date.now()): PlaybackDecision {
  if (item.allowedInCountry === false) return { type: 'blocked', reason: 'GEO_RESTRICTED' };
  if (item.live && isUpcomingAt(item.live, now)) return { type: 'blocked', reason: 'NOT_STARTED_YET' };
  if (item.paid) return { type: 'blocked', reason: 'SUBSCRIPTION_REQUIRED' };

  let source: Omit<PlaybackReady, 'type' | 'title' | 'historyKey' | 'videoId'> | null = null;
  if (item.kind === 'LIVE') {
    // Los directos con DRM solo se reproducen por DASH: su HLS lleva FairPlay.
    if (item.assetId) source = item.drm ? dashSource(item.assetId) : hlsSource(item.assetId);
  } else if (item.kind === 'VIDEO' || item.kind === 'PROGRAM') {
    if (item.playbackId) source = dashSource(item.playbackId);
  }
  if (!source) return { type: 'blocked', reason: 'NO_SOURCE' };

  const vod = item.kind === 'VIDEO' || item.kind === 'PROGRAM';
  return {
    type: 'ready',
    ...source,
    // Un programa se reproduce por su último episodio (subtítulo); un vídeo por su título.
    title: item.kind === 'PROGRAM' ? item.subtitle ?? item.title : item.title,
    historyKey: item.kind !== 'LIVE' ? item.id : null,
    videoId: vod ? item.playbackId : null,
  };
}

/**
 * Los flags `hasDRM` faltan en muchos feeds, así que todo DASH lleva la referencia
 * a la licencia: el reproductor solo la usa si el contenido está protegido.
 */
const dashSource = (id: string) => ({
  uri: `${ZTNR}/${id}.mpd`,
  mimeType: MIME_DASH,
  drmTokenUrl: `${API}/token/${id}`,
  fallbackUri: `${ZTNR}/tv/${id}.mpd`,
});

const hlsSource = (assetId: string) => ({
  uri: `${ZTNR}/${assetId}.m3u8`,
  mimeType: MIME_HLS,
  drmTokenUrl: null,
  fallbackUri: null,
});
