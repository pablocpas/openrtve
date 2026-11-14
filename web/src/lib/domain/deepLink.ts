/** Destino que codifica un enlace de RTVE Play (DeepLink.kt). */
export type DeepLink =
  | { type: 'program'; id: string }
  | { type: 'video'; id: string }
  | { type: 'live'; assetId: string }
  /** `/play/videos/{permalink}/`: el programa hay que buscarlo. */
  | { type: 'programPermalink'; permalink: string }
  /** `/play/videos/directo/{permalink}/`: el directo hay que buscarlo en el feed. */
  | { type: 'livePermalink'; permalink: string };

const DIGITS = /^\d+$/;

/**
 * Función pura sobre la URL, sin red. Acepta `play.rtve.es/pr|v|d/{id}`,
 * `play.rtve.es/content?uri=` y las URL web de `rtve.es/play/...`.
 */
export function parseDeepLink(raw: string): DeepLink | null {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase();
  if (host !== 'rtve.es' && !host.endsWith('.rtve.es')) return null;
  const segments = url.pathname.split('/').filter((s) => s.trim() !== '');

  if (host === 'play.rtve.es') {
    if (segments[0] === 'content') {
      // Una sola decodificación: el parámetro ya viene codificado una vez.
      const inner = url.searchParams.get('uri');
      return inner ? parseDeepLink(inner) : null;
    }
    const id = segments[1];
    if (!id || !DIGITS.test(id)) return null;
    switch (segments[0]) {
      case 'pr': return { type: 'program', id };
      case 'v': return { type: 'video', id };
      case 'd': return { type: 'live', assetId: id };
      default: return null;
    }
  }

  // Solo vídeo: los audios son de RTVE Audio y no se abren aquí.
  if (segments[0] !== 'play' || segments[1] !== 'videos') return null;
  const rest = segments.slice(2);
  if (rest[0] === 'directo') return rest[1] ? { type: 'livePermalink', permalink: rest[1] } : null;
  const last = rest[rest.length - 1];
  if (last && DIGITS.test(last)) return { type: 'video', id: last };
  if (rest.length === 1) return { type: 'programPermalink', permalink: rest[0] };
  return null;
}
