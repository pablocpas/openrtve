# OpenRTVE web

En producción en **[openrtve.pages.dev](https://openrtve.pages.dev)**. Cómo usarla e
instalarla como app, en el [README principal](../README.md#web); aquí, lo necesario
para desarrollarla y publicarla.

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
  para la propia app y con 5 minutos de caché.
- **Los contadores de reproducción**, en `functions/api/playback-metrics.js`: incrementos
  de reproducciones y tiempo que se agregan en una base D1 privada. No pasan vídeos
  por este endpoint.

El catálogo, las imágenes, el vídeo y las licencias van directos del navegador de cada
usuario a RTVE.

Al entrar se muestra un aviso sobre la independencia del proyecto, el origen de los
vídeos y los derechos de sus titulares. Se recuerda durante la sesión de esa pestaña
y puede volver a abrirse desde **Ajustes → Aviso sobre los contenidos**. Un enlace
directo a un vídeo no inicia el reproductor hasta cerrar el aviso.

El aviso y **Ajustes → Derechos y solicitudes de retirada** muestran el correo de
contacto del responsable del proyecto y abren un mensaje con el asunto y los campos
para identificar el contenido y el motivo de la solicitud. Ambos usan el contacto
de `src/lib/ui/rightsContact.ts`.

El aviso y **Ajustes → Privacidad** explican las métricas de reproducción. No hay
cookies de seguimiento, identificadores de personas ni un historial central por
espectador. Se respetan DNT y Global Privacy Control. Detalles y consultas en
[docs/metricas-reproduccion.md](docs/metricas-reproduccion.md).

## Rendimiento

Decisiones medidas que conviene no deshacer sin volver a medir:

- **Imágenes en las variantes de la web oficial** (`src/lib/data/imageSizing.ts`):
  `v/{id}/?w=400`, `p/{id}/imgposter/?h=400`, `?w=1600` en cabeceras… El servicio
  de RTVE redimensiona al vuelo: una variante que nadie ha pedido tarda de medio
  segundo a seis (o responde 503); las que pide www.rtve.es ya están hechas y salen
  en ~50 ms. Pedir "el tamaño justo" de cada tarjeta era más lento, no más rápido.
- **La portada carga primero las filas visibles** y el resto en segundo plano, de
  una en una, con la página ya cargada y el scroll quieto. Cargarlas al llegar a
  ellas cortaba la inercia del scroll en el móvil.
- **Sin `content-visibility` ni `backdrop-filter`**: el primero hacía que las filas
  cambiasen de alto al pintarse (la página saltaba al deslizar); el segundo se
  recalculaba en cada fotograma bajo las barras fijas y fallaba al repintar.
- **El service worker** guarda la app (el reproductor y Shaka, con prioridad baja)
  y las imágenes de RTVE, como la caché de disco de Coil en Android.

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

`npm run build` falla si lo que se descarga para arrancar pasa de 55 KB de JS o 8 KB de
CSS comprimidos (`STARTUP_BUDGET_KB` en `vite.config.ts`): lo demás (pantallas,
reproductor, Shaka) va en trozos aparte.

## Publicar en Cloudflare Pages

Se publica sola: cada push a `main` que toca `web/` pasa por
[`.github/workflows/web.yml`](../.github/workflows/web.yml), que comprueba tipos,
tests y compilación (con su presupuesto de tamaño) y, si todo pasa, sube ese mismo
`dist/` al proyecto `openrtve` de Cloudflare Pages. En los pull requests solo se
comprueba. También se puede lanzar a mano desde *Actions → Web → Run workflow*.

Necesita dos secretos del repositorio (*Settings → Secrets and variables →
Actions*):

- `CLOUDFLARE_ACCOUNT_ID`: el id de la cuenta (`npx wrangler whoami`).
- `CLOUDFLARE_API_TOKEN`: un token de *My Profile → API Tokens → Create Token →
  Custom token* con el permiso *Account → Cloudflare Pages → Edit*.

A mano, desde un ordenador con sesión en Cloudflare:

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

La base D1 de métricas debe tener aplicada la migración antes del despliegue:
`npx wrangler d1 migrations apply PLAYBACK_METRICS --remote`.
Para probarla sin tocar producción, usa `--local` y luego `wrangler pages dev dist`.
