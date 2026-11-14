# OpenRTVE web

La app de OpenRTVE como web instalable (PWA), para escritorio, Android e iPhone/iPad.
Svelte 5 + TypeScript + Vite, con Shaka Player para el vídeo. La lógica es la de la app
de Android, portada: mismas reglas de clasificación, orden de episodios, secciones de
las fichas y "Seguir viendo".

## Qué pasa por el servidor

- **Los archivos de la app** (~1 MB): la primera vez y en cada actualización; después
  arranca desde la caché del navegador.
- **Las recomendaciones de RTVE** (`recomsys`: Similares, búsquedas predefinidas y
  rankings), que solo responden a páginas de `www.rtve.es`. Las reenvía
  `functions/rtve-proxy/recomsys/`, una Cloudflare Pages Function: solo esas rutas, solo
  para la propia app y con 5 minutos de caché. Es lo único que se ejecuta en Cloudflare.

El catálogo, las imágenes, el vídeo y las licencias van directos del navegador de cada
usuario a RTVE.

## Vídeo y DRM

| Navegador | Contenido protegido |
|---|---|
| Chrome, Edge, Firefox, Brave (escritorio y Android) | Widevine |
| Safari (iPhone/iPad, iOS 17.1+) | Variante sin cifrar que RTVE publica para dispositivos sin Widevine (misma calidad) |

## Desarrollo

```sh
npm install
npm run dev        # http://localhost:5173
npm test           # lógica; con LIVE=1 también contra la API real de RTVE
npm run check      # tipos
```

## Publicar en Cloudflare Pages

```sh
npx wrangler login   # una vez
npm run deploy       # compila y sube dist/ al proyecto "openrtve" (wrangler.toml)
```

`npm run deploy` sube también la función de `functions/`. `public/_headers` fija la
caché (los recursos con hash, para siempre; la página y el service worker, siempre
revalidados) y las cabeceras de seguridad. En otro alojamiento hay que reenviar
`/rtve-proxy/recomsys/*` a `https://recomsys.rtve.es/*` de la misma forma; si no, las
búsquedas predefinidas y las filas de rankings muestran un error.

Para probar la función en local: `npm run build && npx wrangler pages dev dist`.
