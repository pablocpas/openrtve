import { describe, expect, it } from 'vitest';
import { sizedImage } from '../src/lib/data/imageSizing';
import { parseMadridTime } from '../src/lib/data/parser';
import { parseDeepLink } from '../src/lib/domain/deepLink';
import { episodeOrder } from '../src/lib/domain/episodeOrder';
import { isAllowedUrl, sanitizeUrl } from '../src/lib/domain/hostPolicy';
import { referenceItem, type CatalogItem, type ProgramDetail } from '../src/lib/domain/models';
import { resolvePlayback } from '../src/lib/domain/playback';

// Los mismos casos que los tests de la app de Android (DeepLinkTest, RtveImageSizingTest...).

describe('enlaces de RTVE', () => {
  it('los enlaces cortos de play.rtve.es llevan a su destino', () => {
    expect(parseDeepLink('https://play.rtve.es/pr/135930')).toEqual({ type: 'program', id: '135930' });
    expect(parseDeepLink('https://play.rtve.es/v/17222216')).toEqual({ type: 'video', id: '17222216' });
    expect(parseDeepLink('https://play.rtve.es/d/1688877')).toEqual({ type: 'live', assetId: '1688877' });
    expect(
      parseDeepLink('https://play.rtve.es/content?uri=https%3A%2F%2Fwww.rtve.es%2Fplay%2Fvideos%2Ftelediario-2%2F21-horas%2F17222216%2F'),
    ).toEqual({ type: 'video', id: '17222216' });
  });

  it('las URL web llevan a vídeo, programa o directo; los audios no', () => {
    expect(parseDeepLink('https://www.rtve.es/play/videos/cine-internacional/la-favorita/16368062/')).toEqual({ type: 'video', id: '16368062' });
    expect(parseDeepLink('https://www.rtve.es/play/audios/ficcion-sonora/el-buscon/17150412/')).toBeNull();
    expect(parseDeepLink('https://www.rtve.es/play/videos/telediario-2/')).toEqual({ type: 'programPermalink', permalink: 'telediario-2' });
    expect(parseDeepLink('https://rtve.es/play/audios/ficcion-sonora')).toBeNull();
    expect(parseDeepLink('https://www.rtve.es/play/videos/directo/la-1/')).toEqual({ type: 'livePermalink', permalink: 'la-1' });
  });

  it('lo ajeno o desconocido se ignora', () => {
    expect(parseDeepLink('https://example.org/play/videos/x/1/')).toBeNull();
    expect(parseDeepLink('https://www.rtve.es/noticias/20260913/algo.shtml')).toBeNull();
    expect(parseDeepLink('https://play.rtve.es/pr/abc')).toBeNull();
    expect(parseDeepLink('no es una url')).toBeNull();
  });
});

describe('tamaño de las imágenes', () => {
  // Las mismas variantes que pide la web oficial: casi siempre ya están en la caché de RTVE.
  it('vídeos: 400 de ancho en tarjetas, 1600 en cabeceras; la carátula editorial se conserva', () => {
    expect(sizedImage('https://img.rtve.es/v/9/?w=960', 340)).toBe('https://img.rtve.es/v/9/?w=400');
    expect(sizedImage('https://img.rtve.es/v/9/horizontal?w=960', 200)).toBe('https://img.rtve.es/v/9/?w=400');
    expect(sizedImage('https://img.rtve.es/v/9/horizontal2?w=960', 340)).toBe('https://img.rtve.es/v/9/horizontal2?w=400');
    expect(sizedImage('https://img.rtve.es/v/9/horizontal2?w=1600', 1280)).toBe('https://img.rtve.es/v/9/horizontal2?w=1600');
    expect(sizedImage('https://img.rtve.es/v/9/?w=1600', 1280)).toBe('https://img.rtve.es/v/9/?w=1600');
  });

  it('pósters y cuadradas: 400 de alto', () => {
    expect(sizedImage('https://img.rtve.es/v/9/vertical?w=480', 220)).toBe('https://img.rtve.es/v/9/vertical/?h=400');
    expect(sizedImage('https://img.rtve.es/v/9/vertical2?w=480', 220)).toBe('https://img.rtve.es/v/9/vertical2/?h=400');
    expect(sizedImage('https://img.rtve.es/p/123?imgProgApi=imgPoster&w=480', 220)).toBe('https://img.rtve.es/p/123/imgposter/?h=400');
    expect(sizedImage('https://img.rtve.es/p/123?imgProgApi=imgBackground&w=480', 220)).toBe('https://img.rtve.es/p/123/imgbackground/?h=400');
  });

  it('programas: portada a 400 o 1600 y logo a 320', () => {
    expect(sizedImage('https://img.rtve.es/p/123?imgProgApi=imgPortada&w=960', 340)).toBe('https://img.rtve.es/p/123/imgportada/?w=400');
    expect(sizedImage('https://img.rtve.es/p/123?imgProgApi=imgPortada&w=1600', 1920)).toBe('https://img.rtve.es/p/123/imgportada/?w=1600');
    expect(sizedImage('https://img.rtve.es/p/123?imgProgApi=logo', 120)).toBe('https://img.rtve.es/p/123/?w=320&imgProgApi=logo');
  });

  it('los originales pasan por el redimensionador con los mismos dos anchos', () => {
    expect(sizedImage('https://img.rtve.es/imagenes/cine-de-siempre/1768405234614.jpg', 300)).toBe(
      'https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fimagenes%2Fcine-de-siempre%2F1768405234614.jpg',
    );
    expect(sizedImage('https://img.rtve.es/i/?w=400&i=1665049167787.jpg', 900)).toBe('https://img.rtve.es/i/?w=1600&i=1665049167787.jpg');
  });

  it('los estáticos, otros hosts y los /p/ con nombre de fichero no se tocan', () => {
    expect(sizedImage('https://img.rtve.es/resources/x/1.jpg', 300)).toBeNull();
    expect(sizedImage('https://img2.rtve.es/p/1?w=1', 300)).toBeNull();
    // RTVE responde 503 a `?w=` en estos y 404 desde su redimensionador.
    expect(sizedImage('https://img.rtve.es/p/bluey_1028792.png', 300)).toBeNull();
    expect(sizedImage('https://img.rtve.es/p/1?w=1', 0)).toBeNull();
  });
});

describe('hosts admitidos', () => {
  it('solo HTTPS a rtve.es y sus subdominios, sin credenciales ni puertos raros', () => {
    expect(isAllowedUrl('https://api.rtve.es/api/videos/1.json')).toBe(true);
    expect(isAllowedUrl('http://api.rtve.es/x')).toBe(false);
    expect(isAllowedUrl('https://rtve.es.example.org/x')).toBe(false);
    expect(isAllowedUrl('https://user:pass@api.rtve.es/x')).toBe(false);
    expect(isAllowedUrl('https://api.rtve.es:8443/x')).toBe(false);
    expect(sanitizeUrl('http://img.rtve.es/p/1')).toBe('https://img.rtve.es/p/1');
  });
});

describe('reproducción', () => {
  const video = (patch: Partial<CatalogItem> = {}): CatalogItem => ({ ...referenceItem('17232941', 'VIDEO', 'La Regenta'), ...patch });

  it('un vídeo va por DASH con licencia y variante sin DRM', () => {
    const decision = resolvePlayback(video());
    expect(decision).toMatchObject({
      type: 'ready',
      uri: 'https://ztnr.rtve.es/ztnr/17232941.mpd',
      drmTokenUrl: 'https://api.rtve.es/api/token/17232941',
      fallbackUri: 'https://ztnr.rtve.es/ztnr/tv/17232941.mpd',
      historyKey: '17232941',
    });
  });

  it('un directo con DRM va por DASH; sin DRM, por HLS', () => {
    const live = (drm: boolean): CatalogItem => ({ ...referenceItem('1688877', 'LIVE', 'La 1'), assetId: '1688877', drm });
    expect(resolvePlayback(live(true))).toMatchObject({ uri: 'https://ztnr.rtve.es/ztnr/1688877.mpd', historyKey: null });
    expect(resolvePlayback(live(false))).toMatchObject({ uri: 'https://ztnr.rtve.es/ztnr/1688877.m3u8', drmTokenUrl: null });
  });

  it('geobloqueo, pago y emisiones futuras se explican', () => {
    expect(resolvePlayback(video({ allowedInCountry: false }))).toEqual({ type: 'blocked', reason: 'GEO_RESTRICTED' });
    expect(resolvePlayback(video({ paid: true }))).toEqual({ type: 'blocked', reason: 'SUBSCRIPTION_REQUIRED' });
    const upcoming = video({
      kind: 'LIVE',
      assetId: '1',
      live: { isOnAir: false, startsAtMillis: 2_000, durationMinutes: 60, progressPercent: null, channelLogoUrl: null, category: null },
    });
    expect(resolvePlayback(upcoming, 1_000)).toEqual({ type: 'blocked', reason: 'NOT_STARTED_YET' });
  });
});

describe('orden de episodios', () => {
  const program = (patch: Partial<ProgramDetail>): ProgramDetail => ({
    id: '1', title: '', description: null, imageUrl: null, emission: null, seasons: [], webUrl: null, programTypeId: null,
    inEmission: true, isComplete: false, showFragments: false, year: null, ageRating: null, genres: [], director: null,
    cast: [], originalLanguage: null, country: null, programType: null, ...patch,
  });

  it('una serie completa se lee del principio; un programa en emisión, del final', () => {
    expect(episodeOrder(program({ isComplete: true }))).toBe('OLDEST_FIRST');
    expect(episodeOrder(program({ inEmission: true }))).toBe('NEWEST_FIRST');
    expect(episodeOrder(program({ inEmission: true }), false)).toBe('OLDEST_FIRST');
    expect(episodeOrder(program({ inEmission: false, programTypeId: '137650' }))).toBe('NEWEST_FIRST');
  });
});

describe('horas del feed', () => {
  it('se leen en hora de Madrid, también en verano', () => {
    expect(new Date(parseMadridTime('15-01-2026 16:10:00')!).toISOString()).toBe('2026-01-15T15:10:00.000Z');
    expect(new Date(parseMadridTime('15-07-2026 16:10:00')!).toISOString()).toBe('2026-07-15T14:10:00.000Z');
    expect(parseMadridTime('mañana')).toBeNull();
  });
});
