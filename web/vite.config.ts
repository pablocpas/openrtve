/// <reference types="vitest/config" />
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { gzipSync } from 'node:zlib';
import { defineConfig, type Plugin } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import pkg from './package.json' with { type: 'json' };

/**
 * Escribe en `dist/sw.js` la lista de archivos de la app y una versión ligada a su
 * contenido: el service worker los guarda todos al instalarse (la PWA arranca sin
 * red desde la primera visita) y cada compilación distinta estrena caché. Los trozos
 * que la página no carga al arrancar (el reproductor, Shaka) van en una lista aparte
 * que se descarga con prioridad baja, para no competir con la portada.
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
      const html = readFileSync(join(outDir, 'index.html'), 'utf8');
      const isLater = (f: string) => f.startsWith('assets/') && /\.(js|css)$/.test(f) && !html.includes(f);
      const version = precache.filter((f) => f.startsWith('assets/')).join('|');
      let hash = 0;
      for (const char of version) hash = (Math.imul(hash, 31) + char.charCodeAt(0)) | 0;
      checkStartupBudget(outDir, precache.filter((f) => f.startsWith('assets/') && !isLater(f)));
      const swPath = join(outDir, 'sw.js');
      const sw = readFileSync(swPath, 'utf8')
        .replace("'__BUILD_VERSION__'", JSON.stringify(`${pkg.version}-${(hash >>> 0).toString(36)}`))
        .replace("['__PRECACHE_LIST__']", JSON.stringify(precache.filter((f) => !isLater(f)).map((f) => `./${f}`)))
        .replace("['__PRECACHE_LATER__']", JSON.stringify(precache.filter(isLater).map((f) => `./${f}`)));
      writeFileSync(swPath, sw);
    },
  };
}

/**
 * Lo que se descarga para arrancar, comprimido, no puede crecer sin darse cuenta: la
 * compilación falla si se pasa. Si hace falta más, que sea en un trozo aparte.
 */
const STARTUP_BUDGET_KB = { js: 55, css: 8 };

function checkStartupBudget(outDir: string, startup: string[]) {
  const gzipKb = (ext: string) =>
    startup.filter((f) => f.endsWith(ext)).reduce((sum, f) => sum + gzipSync(readFileSync(join(outDir, f)), { level: 9 }).length, 0) / 1024;
  const js = gzipKb('.js');
  const css = gzipKb('.css');
  console.log(`Arranque: ${js.toFixed(1)} KB de JS y ${css.toFixed(1)} KB de CSS (gzip)`);
  if (js > STARTUP_BUDGET_KB.js || css > STARTUP_BUDGET_KB.css) {
    throw new Error(`El arranque supera el presupuesto (${STARTUP_BUDGET_KB.js} KB de JS, ${STARTUP_BUDGET_KB.css} KB de CSS)`);
  }
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
