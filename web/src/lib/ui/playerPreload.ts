/**
 * El reproductor y Shaka (~650 KB sin comprimir) se descargan aparte. Cuando es
 * probable que se vaya a reproducir (se abre una ficha), se piden y se evalúan antes,
 * y se abre la conexión con el servidor de vídeo: al pulsar "Reproducir" ya está todo.
 */
let started = false;

export const loadPlayerScreen = () => import('../../screens/Player.svelte');
export const loadShaka = () => import('shaka-player/dist/shaka-player.compiled-es2021.js').then((m) => m.default);

export function preloadPlayer() {
  if (started || typeof document === 'undefined') return;
  started = true;
  const link = document.createElement('link');
  link.rel = 'preconnect';
  link.href = 'https://ztnr.rtve.es';
  link.crossOrigin = 'anonymous';
  document.head.append(link);
  // Cuando el hilo principal esté libre, para no frenar la propia ficha.
  const run = () => void Promise.all([loadPlayerScreen(), loadShaka()]).catch(() => (started = false));
  if ('requestIdleCallback' in window) requestIdleCallback(run, { timeout: 2000 });
  else setTimeout(run, 500);
}
