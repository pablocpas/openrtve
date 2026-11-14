/// <reference types="vitest/config" />
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { defineConfig, type Plugin } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import pkg from './package.json' with { type: 'json' };

/**
 * Escribe en `dist/sw.js` la lista de archivos de la app y una versión ligada a su
 * contenido: el service worker los guarda todos al instalarse (la PWA arranca sin
 * red desde la primera visita) y cada compilación distinta estrena caché.
 */
function serviceWorkerPrecache(): Plugin {
  let outDir = 'dist';
  return {
    name: 'openrtve-sw-precache',
    apply: 'build',
    configResolved(config) {
      outDir = config.build.outDir;
    },
    closeBundle() {
      const files: string[] = [];
      const walk = (dir: string) => {
        for (const name of readdirSync(dir)) {
          const path = join(dir, name);
          if (statSync(path).isDirectory()) walk(path);
          else files.push(relative(outDir, path).split('\\').join('/'));
        }
      };
      walk(outDir);
      const precache = files.filter((f) => f !== 'sw.js' && !f.startsWith('_') && f !== 'index.html' && !f.endsWith('.map')).sort();
      const version = precache.filter((f) => f.startsWith('assets/')).join('|');
      let hash = 0;
      for (const char of version) hash = (Math.imul(hash, 31) + char.charCodeAt(0)) | 0;
      const swPath = join(outDir, 'sw.js');
      const sw = readFileSync(swPath, 'utf8')
        .replace("'__BUILD_VERSION__'", JSON.stringify(`${pkg.version}-${(hash >>> 0).toString(36)}`))
        .replace("['__PRECACHE_LIST__']", JSON.stringify(precache.map((f) => `./${f}`)));
      writeFileSync(swPath, sw);
    },
  };
}

/** Lo mismo que hace la función de Cloudflare (functions/rtve-proxy/recomsys), para desarrollo. */
const rtveProxy = {
  '/rtve-proxy/recomsys': {
    target: 'https://recomsys.rtve.es',
    changeOrigin: true,
    rewrite: (path: string) => path.replace(/^\/rtve-proxy\/recomsys/, ''),
    headers: { Origin: 'https://www.rtve.es', Referer: 'https://www.rtve.es/' },
  },
};

// Rutas relativas: la app funciona servida desde la raíz o desde una subcarpeta
// (GitHub Pages, un NAS...) sin tocar nada.
export default defineConfig({
  base: './',
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  plugins: [svelte(), serviceWorkerPrecache()],
  server: { proxy: rtveProxy },
  preview: { proxy: rtveProxy },
  build: {
    target: 'es2022',
    cssCodeSplit: true,
    // Shaka va en su propio trozo y solo se descarga al abrir el reproductor.
    chunkSizeWarningLimit: 700,
  },
  test: {
    include: ['tests/**/*.test.ts'],
  },
});
