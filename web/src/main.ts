import { mount } from 'svelte';
import './app.css';
import App from './App.svelte';
import { nav } from './lib/ui/nav.svelte';

/**
 * "Compartir → OpenRTVE" (share target de la PWA): el sistema abre la app con el
 * enlace en `?url=` o dentro de `?text=`. Se convierte en la ruta interna del enlace
 * y se limpia la dirección, para que recargar no lo vuelva a abrir.
 */
function takeSharedLink() {
  const params = new URLSearchParams(location.search);
  const shared = params.get('url') || params.get('text')?.match(/https?:\/\/\S+/)?.[0];
  if (!params.has('url') && !params.has('text') && !params.has('title')) return;
  const hash = shared ? `#/enlace?url=${encodeURIComponent(shared)}` : location.hash;
  history.replaceState(null, '', `${location.pathname}${hash}`);
}

takeSharedLink();
nav.init();

const app = mount(App, { target: document.getElementById('app')! });

// La PWA funciona sin conexión con lo último que se vio; el service worker solo
// guarda la propia app (el catálogo vive en IndexedDB y el vídeo nunca se guarda).
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  });
}

export default app;
