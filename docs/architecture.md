# Arquitectura inicial

## Principios

OpenRTVE empieza como una aplicación de un solo módulo. La separación es por
paquetes y por dirección de dependencias, no por una colección de módulos
Gradle vacíos:

```text
UI móvil ─┐  NavigationViewModel (pestaña + pila de Destination)
          ├──> PortadaViewModel ──┐
UI TV ────┘    ModuleViewModel   ├─> CatalogRepository ─> HTTPS + caché
               ProgramViewModel  │
               SearchViewModel   │
               ExploreViewModel ─┘

UI ─> PlaybackResolver ─> PlayerActivity ─> MediaController
                                      └───> MediaSessionService + ExoPlayer
```

- Estado inmutable mediante `StateFlow`; un ViewModel por pantalla, creado
  con clave (`viewModel(key = "portada-$url")`) para que cada portada, módulo
  o programa conserve su estado al navegar.
- Navegación propia: tres pestañas y una pila de destinos por pestaña en
  `NavigationViewModel`. Una librería de navegación no aporta nada con tres
  tipos de destino.
- La presentación de cada fila (`RowLayout`) se deduce del `tipo` editorial
  en el parser; la UI solo decide tamaños.
- La portada carga cada fila por separado (máximo cuatro en paralelo) para
  que una sección lenta o rota no bloquee las demás; las vacías se ocultan.
  El patrón de secciones + carrusel + tarjeta con imagen procede de Findroid.
- Toda captura de excepciones en corrutinas relanza `CancellationException`:
  un `runCatching` alrededor de una suspensión convertiría una cancelación en
  un error visible.
- Un repositorio como única entrada al catálogo.
- JSON dinámico convertido inmediatamente a modelos pequeños del dominio.
- Inyección manual en `Application`; no hay un grafo que justifique Hilt.
- Corrutinas para trabajo bloqueante y acceso de UI consciente del ciclo de
  vida.
- La caché es una mejora de disponibilidad, nunca la fuente de URLs temporales,
  tokens o licencias.

## Móvil y TV

Datos, estado y política son comunes. Las superficies no lo son:

- móvil usa Material 3, ventana edge-to-edge y rejilla adaptativa;
- TV sigue las guías de Compose for TV (patrón de la muestra JetStream):
  `NavigationDrawer` en el borde izquierdo, `Carousel` destacado en la portada,
  filas con `focusRestorer`, tarjetas que escalan al enfocar, márgenes de
  overscan de 48/27 dp y nada que dependa del táctil; atrás lo maneja el
  mando;
- el player se aloja en un único `MediaSessionService`, controlado por una
  actividad efímera en ambos dispositivos. La actividad solo carga un item si
  su `mediaId` difiere del que ya tiene la sesión: rotar, entrar en PiP o
  volver desde la notificación reanuda en vez de reiniciar. PiP y pantalla
  inmersiva se activan solo para vídeo.

No se decide la UI por orientación o por un ancho fijo. La actividad elegida
por el launcher determina móvil/TV y cada shell responde al espacio disponible.

## Reproductor

`PlayerView` de Media3 con un layout de controles propio
(`res/layout/player_controls.xml`) que replica la estructura del reproductor
de Findroid: acciones arriba, transporte en el centro, tiempos y barra abajo.
Los botones con id `exo_audio_track`, `exo_subtitle` y `exo_playback_speed`
los enlaza `PlayerControlView`, que abre sus propios selectores; los nuestros
(`player_back`, `player_pip`, `player_lock`, `player_aspect`) los maneja la
actividad. `PlayerGestures` añade toque, doble toque y deslizamientos. Findroid
es GPLv3: se toma el diseño, no el código.

Ciclo de vida, según la guía de Android para reproductores de vídeo: al salir
de la pantalla (`onStop`) el vídeo se pausa salvo en PiP, y al cerrarla (atrás
o cerrar la ventana PiP) se detiene y vacía la sesión. Radio y pódcasts siguen
sonando en segundo plano con su notificación, porque eso es lo estándar en
apps de audio; es desactivable en ajustes.

## Red y datos

`RtveHostPolicy` (dominio) es la única allowlist: solo HTTPS, puerto estándar,
sin credenciales embebidas y host `rtve.es` o subdominio real. La aplican el
cliente HTTPS en cada salto de redirección, el parser sobre `urlContent` y las
URLs de imagen (elevando `http://` a HTTPS), y el resolver sobre `filePath`.
`SafeHttpsClient` corre sobre un único `OkHttpClient` compartido con Coil (una
conexión HTTP/2 multiplexada para catálogo e imágenes, gzip, pool), aplica la
allowlist en un interceptor de red (cada salto, redirecciones incluidas),
limita el tamaño de respuesta y usa los certificados del sistema. El pipeline de Media3 sigue los 302 de `ztnr` por su
cuenta; la Network Security Config impide cualquier salto en claro.

Los manifests de portada se cachean dos minutos y los módulos cinco. Con copia
local, la petición lleva `If-None-Match`: RTVE responde 304 si no ha cambiado y
la copia vuelve a contar como fresca sin descargar nada. Si falla la red se
admite una copia de hasta 24 horas y la UI la marca como stale. El
parser conserva strings desconocidos, acepta IDs numéricos o textuales y
aplana el nivel adicional de las colecciones.

## Cuándo extraer módulos

Solo extraeremos un módulo Gradle cuando exista al menos una de estas razones:

1. frontera de seguridad o API que deba ser imposible atravesar;
2. tiempo de compilación que mejore de forma medible;
3. reutilización real por otro artefacto;
4. equipo o ciclo de entrega independiente.

Los candidatos naturales, cuando se implementen, son reproducción/descargas,
cuenta y un posible cliente compartido. No se crean por anticipado.
