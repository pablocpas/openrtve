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

/** URL que realmente se pide: la misma o, para los hosts sin CORS, la del reenvío. */
function requestUrl(url: string): { href: string; proxied: boolean } {
  const parsed = new URL(url);
  const prefix = PROXIED_HOSTS[parsed.hostname];
  if (!prefix || typeof document === 'undefined') return { href: url, proxied: false };
  return { href: new URL(`./${prefix}${parsed.pathname}${parsed.search}`, document.baseURI).href, proxied: true };
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
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
  const target = requestUrl(url);
  let response: Response;
  try {
    response = await fetch(target.href, {
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
  if (response.url && !target.proxied) requireAllowed(response.url);
  return response.text();
}
