import { requireAllowed } from '../domain/hostPolicy';

/** Por qué no llegó el contenido; la UI lo explica en el sitio, con su reintento. */
export type LoadError =
  | { type: 'offline' }
  | { type: 'timeout' }
  | { type: 'http'; status: number }
  | { type: 'unknown' };

export class HttpStatusError extends Error {
  constructor(readonly status: number) {
    super(`HTTP ${status}`);
  }
}

export class TimeoutError extends Error {}

export function toLoadError(error: unknown): LoadError {
  if (error instanceof HttpStatusError) return { type: 'http', status: error.status };
  if (error instanceof TimeoutError) return { type: 'timeout' };
  if (error instanceof DOMException && error.name === 'TimeoutError') return { type: 'timeout' };
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return { type: 'offline' };
  // `fetch` falla con TypeError cuando no hay red o se corta la conexión.
  if (error instanceof TypeError) return { type: 'offline' };
  return { type: 'unknown' };
}

export const isAbort = (error: unknown) => error instanceof DOMException && error.name === 'AbortError';

const TIMEOUT_MS = 15_000;

/**
 * Hosts de RTVE que solo responden a páginas de `www.rtve.es` (sin CORS para nadie
 * más): las recomendaciones de `recomsys` (Similares, búsquedas predefinidas y
 * rankings). Se piden a la propia web, que las reenvía: en Cloudflare Pages, la
 * función `functions/rtve-proxy/recomsys/`; en desarrollo, el proxy de Vite.
 */
const PROXIED_HOSTS: Record<string, string> = { 'recomsys.rtve.es': 'rtve-proxy/recomsys' };

/**
 * Portadas de sección (`www.rtve.es/play/<sección>/index_apps.json`): RTVE pone CORS
 * en unas y no en otras. Se piden directamente y, si el navegador las bloquea, por
 * `functions/rtve-proxy/portada/`; las bloqueadas se recuerdan para no repetir el intento.
 */
const PORTADA_PATH = /^\/play(\/[a-z0-9-]+)+\/index_apps\.json$/;
const blockedPortadas = new Set<string>();

/** Reenvío de la propia web para una URL de RTVE; `always` si el directo nunca funciona. */
function proxyFor(url: string): { href: string; always: boolean } | null {
  if (typeof document === 'undefined') return null;
  const parsed = new URL(url);
  const prefix = PROXIED_HOSTS[parsed.hostname];
  if (prefix) return { href: new URL(`./${prefix}${parsed.pathname}${parsed.search}`, document.baseURI).href, always: true };
  if (parsed.hostname === 'www.rtve.es' && !parsed.search && PORTADA_PATH.test(parsed.pathname)) {
    return { href: new URL(`./rtve-proxy/portada${parsed.pathname}`, document.baseURI).href, always: blockedPortadas.has(url) };
  }
  return null;
}

export interface FetchOptions {
  signal?: AbortSignal;
  /**
   * Pide al navegador que revalide con el servidor (ETag/Last-Modified): un 304 no
   * descarga nada. Se hace con `cache: 'no-cache'` y no con `If-None-Match` a mano,
   * que no es una cabecera simple y forzaría una petición previa de CORS.
   */
  revalidate?: boolean;
  /** Para tokens de DRM: ni se guarda ni se reutiliza. */
  noStore?: boolean;
}

/**
 * GET de texto a un host de RTVE. Solo HTTPS a `rtve.es` y sus subdominios, sin
 * cabeceras propias (así no hay petición previa de CORS) y con tiempo máximo.
 */
export async function getText(url: string, options: FetchOptions = {}): Promise<string> {
  requireAllowed(url);
  const proxy = proxyFor(url);
  if (proxy?.always) return fetchText(proxy.href, options, true);
  try {
    return await fetchText(url, options, false);
  } catch (error) {
    // Sin CORS, el navegador da el mismo TypeError que sin red: se prueba el reenvío.
    if (!proxy || !(error instanceof TypeError) || (typeof navigator !== 'undefined' && navigator.onLine === false)) throw error;
    const text = await fetchText(proxy.href, options, true);
    blockedPortadas.add(url);
    return text;
  }
}

async function fetchText(href: string, options: FetchOptions, proxied: boolean): Promise<string> {
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
  let response: Response;
  try {
    response = await fetch(href, {
      signal,
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      cache: options.noStore ? 'no-store' : options.revalidate ? 'no-cache' : 'default',
    });
  } catch (error) {
    if (timeout.aborted && !options.signal?.aborted) throw new TimeoutError('Tiempo agotado');
    throw error;
  }
  if (!response.ok) throw new HttpStatusError(response.status);
  // Las redirecciones también deben quedarse en RTVE.
  if (response.url && !proxied) requireAllowed(response.url);
  return response.text();
}
