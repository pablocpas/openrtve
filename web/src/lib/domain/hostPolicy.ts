/**
 * Única definición de qué hosts remotos acepta la app (RtveHostPolicy.kt). La usan
 * el cliente HTTP, el parser (imágenes) y el reproductor.
 */
const ROOT_DOMAIN = 'rtve.es';

export function isAllowedUrl(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  const host = url.hostname.toLowerCase();
  return (
    url.protocol === 'https:' &&
    url.username === '' &&
    url.password === '' &&
    (url.port === '' || url.port === '443') &&
    (host === ROOT_DOMAIN || host.endsWith(`.${ROOT_DOMAIN}`))
  );
}

export function requireAllowed(value: string): string {
  if (!isAllowedUrl(value)) throw new Error('Host no permitido');
  return value;
}

/**
 * Devuelve la URL lista para usarse o `null` si no es admisible. Las imágenes de
 * los feeds llegan a veces en `http://`; se elevan a HTTPS antes de validar.
 */
export function sanitizeUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  const upgraded = value.trim().replace(/^http:\/\//i, 'https://');
  return isAllowedUrl(upgraded) ? upgraded : null;
}
