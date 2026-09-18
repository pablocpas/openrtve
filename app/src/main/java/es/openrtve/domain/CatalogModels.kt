package es.openrtve.domain

object RtveUrls {
    const val TV_HOME = "https://www.rtve.es/play/index_apps.json"
    const val RADIO_HOME = "https://www.rtve.es/play/radio/index_apps.json"
    const val REMOTE_CONFIG = "https://www.rtve.es/m/configs/rtve_play/2026/estructura2.json"
    const val QUICK_SEARCHES = "https://www.rtve.es/m/configs/buscador/predefinidos.json"
    const val SEARCH = "https://api.rtve.es/api/search/results"
}

/** Qué recorte pinta cada presentación, con sus respaldos; la única definición para móvil y TV. */
fun CatalogItem.imageFor(layout: RowLayout): String? = when (layout) {
    RowLayout.POSTER -> posterUrl ?: imageUrl
    RowLayout.POSTER_TALL -> tallPosterUrl ?: posterUrl ?: imageUrl
    RowLayout.SQUARE -> squareUrl ?: imageUrl ?: posterUrl
    RowLayout.HERO, RowLayout.FEATURED, RowLayout.LANDSCAPE, RowLayout.RANKED -> imageUrl
}

/** Item mínimo que solo aporta id y tipo: la pantalla de destino carga la ficha completa. */
fun referenceItem(id: String, kind: ContentKind, title: String, imageUrl: String? = null) = CatalogItem(
    id = id,
    playbackId = if (kind == ContentKind.PROGRAM) null else id,
    assetId = null,
    title = title,
    subtitle = null,
    imageUrl = imageUrl,
    kind = kind,
    directQualityUrl = null,
    allowedInCountry = null,
    loginRequired = false,
    paid = false,
    drm = false,
    programId = if (kind == ContentKind.PROGRAM) id else null,
)

/** Fila sintética de colección (`collection/{id}.json`), para abrirla desde un enlace. */
fun collectionRow(url: String, title: String) = HomeRow(
    id = -1,
    title = title,
    order = 0,
    moduleType = "Collection",
    presentation = "ColeccionPoster",
    contentUrl = url,
    layout = RowLayout.POSTER,
)

/** Fila sintética de directos sobre un feed de `lives`, para abrir un directo que no viene de una portada. */
fun liveRow(url: String, title: String = "") = HomeRow(
    id = -1,
    title = title,
    order = 0,
    moduleType = "livesCollection",
    presentation = "directosTV",
    contentUrl = url,
    layout = RowLayout.LANDSCAPE,
)

/**
 * Cómo pinta la UI una fila; se deduce del `tipo` editorial y nunca es un enum
 * cerrado en el feed. Cada valor corresponde a una presentación distinta en
 * RTVE Play: dos `tipo` distintos de la API no se funden en una misma tarjeta.
 */
enum class RowLayout {
    /** `ColeccionDestacado`: carrusel a ancho completo, un item por página. */
    HERO,
    /** `ColeccionSuperDestacado`: un solo destacado con imagen, título, descripción y botón "Ver". */
    FEATURED,
    /** `ColeccionPoster`, `videoPoster`, `programas`: pósters verticales 2:3. */
    POSTER,
    /** `ColeccionSuper`: pósters altos 1:2 con la imagen `imgCol`, distinta del póster. */
    POSTER_TALL,
    /** `ColeccionCuadrado*`: tarjetas cuadradas (radio, música). */
    SQUARE,
    /** `ColeccionApaisado`, `videos`, `directosTV*`: tarjetas apaisadas 16:9. */
    LANDSCAPE,
    /** `ColleccionTops`, `Tops`: apaisadas con su posición en el ranking. */
    RANKED,
    ;

    /** Tarjetas más altas que anchas: dos líneas de título y sin subtítulo. */
    val isVertical: Boolean
        get() = when (this) {
            POSTER, POSTER_TALL -> true
            HERO, FEATURED, SQUARE, LANDSCAPE, RANKED -> false
        }

    /** En rejilla los destacados no tienen sentido: se degradan a apaisada. */
    val gridLayout: RowLayout
        get() = when (this) {
            HERO, FEATURED -> LANDSCAPE
            POSTER, POSTER_TALL, SQUARE, LANDSCAPE, RANKED -> this
        }
}

data class HomeFeed(
    val id: String?,
    val title: String,
    val rows: List<HomeRow>,
)

/** A qué apunta un enlace de una fila `links`; `enlaceExterno` sale de la app y no se modela. */
enum class LinkKind { COLLECTION, PORTADA, PROGRAM, VIDEO, AUDIO }

/** Acceso editorial de una fila `links`: viene completo en la portada, sin descarga. */
data class HomeLink(
    val title: String,
    val imageUrl: String?,
    val url: String,
    val kind: LinkKind,
) {
    /** Último segmento numérico de la URL de la API (`/api/programas/1234.json` -> `1234`). */
    val apiId: String?
        get() = API_ID.find(url)?.groupValues?.get(1)

    private companion object {
        val API_ID = Regex("/(\\d+)(?:\\.json)?/?$")
    }
}

data class HomeRow(
    /** Posición estable dentro de la portada; los títulos y `orden` pueden repetirse. */
    val id: Int,
    val title: String,
    val order: Int,
    val moduleType: String?,
    val presentation: String?,
    val contentUrl: String?,
    val layout: RowLayout = RowLayout.LANDSCAPE,
    /** Fila `links`: sus accesos vienen en la propia portada y `contentUrl` no se usa. */
    val links: List<HomeLink> = emptyList(),
) {
    /** Su contenido ya viene en la portada: no hay módulo que descargar. */
    val isInline: Boolean
        get() = links.isNotEmpty()

    /** Se puede pintar: o trae su contenido, o tiene una fuente remota admitida. */
    val isRenderable: Boolean
        get() = isInline || contentUrl != null

    /** Módulo de emisoras de radio: llega sin `urlContent`; la fuente está en la configuración remota. */
    val isRadioLivesModule: Boolean
        get() = moduleType.equals("moduloDirectoRadio", ignoreCase = true) || presentation.equals("moduloDirectoRadio", ignoreCase = true)

    /** Fila de directos: su contenido caduca en minutos. */
    val isLive: Boolean
        get() = presentation?.lowercase()?.let { it.startsWith("directos") || it == "modulodirectoradio" } == true ||
            moduleType.equals("livesCollection", ignoreCase = true)
}

enum class ContentKind {
    PROGRAM,
    VIDEO,
    AUDIO,
    LIVE,
    UNKNOWN,
}

data class CatalogItem(
    val id: String,
    /** ID del medio reproducible. `null` cuando el item no apunta a ningún medio (p. ej. un programa sin último episodio). */
    val playbackId: String?,
    val assetId: String?,
    val title: String,
    val subtitle: String?,
    /** Imagen apaisada. */
    val imageUrl: String?,
    val kind: ContentKind,
    /** Póster vertical 2:3. */
    val posterUrl: String? = null,
    /** Imagen cuadrada 1:1 (radio, música). */
    val squareUrl: String? = null,
    /** Imagen `imgCol` de un programa: el recorte alto (1:2) que usa `ColeccionSuper`; no es el póster. */
    val tallPosterUrl: String? = null,
    val directQualityUrl: String?,
    val allowedInCountry: Boolean?,
    val loginRequired: Boolean,
    val paid: Boolean,
    val drm: Boolean,
    /** Programa al que pertenece el medio, si el feed lo indica. */
    val programId: String? = null,
    val durationMs: Long? = null,
    /** Fecha tal como la entrega el feed (`dd-MM-yyyy HH:mm:ss`); la UI la formatea. */
    val publicationDate: String? = null,
    val episode: Int? = null,
    val seasonTitle: String? = null,
    /** Solo en directos: emisión en curso o programada. */
    val live: LiveInfo? = null,
    /** Página web del item en rtve.es; sirve para compartir y para casar enlaces. */
    val webUrl: String? = null,
) {
    val needsAccount: Boolean get() = loginRequired || paid || drm
}

/**
 * Lo que el feed de directos sabe de la emisión: si está en el aire, cuándo
 * empieza, cuánto dura y el logo del canal. Las horas del feed son de Madrid.
 */
data class LiveInfo(
    val isOnAir: Boolean,
    val startsAtMillis: Long?,
    val durationMinutes: Int?,
    /** Progreso que envía el feed; la UI lo recalcula con el reloj si conoce inicio y duración. */
    val progressPercent: Int?,
    val channelLogoUrl: String?,
    val category: String?,
    /** Emisora de radio: el stream es HLS pero solo audio. */
    val isAudio: Boolean = false,
) {
    fun progressAt(nowMillis: Long): Float? {
        val start = startsAtMillis
        val minutes = durationMinutes
        if (start != null && minutes != null && minutes > 0) {
            return ((nowMillis - start).toFloat() / (minutes * 60_000L)).coerceIn(0f, 1f)
        }
        return progressPercent?.let { (it / 100f).coerceIn(0f, 1f) }
    }

    /** Programado y aún no empezado según el reloj. */
    fun isUpcomingAt(nowMillis: Long): Boolean =
        !isOnAir && startsAtMillis != null && startsAtMillis > nowMillis
}

data class CatalogModule(
    val title: String,
    val items: List<CatalogItem>,
)

data class CatalogPage(
    val items: List<CatalogItem>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

data class ProgramSeason(
    val id: String,
    val title: String,
    val episodeCount: Int?,
)

data class ProgramDetail(
    val id: String,
    val title: String,
    /** Puede contener HTML; la UI lo renderiza. */
    val description: String?,
    val imageUrl: String?,
    val emission: String?,
    val seasons: List<ProgramSeason>,
    val webUrl: String? = null,
    /** Programa de radio: sus episodios están en `audios.json`. */
    val isRadio: Boolean = false,
)

data class CatalogLoad<T>(
    val value: T,
    val isStale: Boolean,
)

data class SearchResults(
    val programs: List<CatalogItem>,
    val videos: List<CatalogItem>,
) {
    val isEmpty: Boolean get() = programs.isEmpty() && videos.isEmpty()
}

/** Pestaña de búsqueda predefinida ("Más buscados", "Series TV"...). */
data class QuickFilter(
    val title: String,
    val contentUrl: String,
)

/** Entrada del menú de Explorar: una portada, o un canal temático en directo (`broadcasts/{id}.json`). */
data class ExploreCategory(
    val title: String,
    val imageUrl: String?,
    val contentUrl: String,
    val isLive: Boolean = false,
)

/**
 * Sección de Explorar. Sin [title] es el bloque principal del menú, que la app
 * oficial lista sin cabecera; [isKids] es el bloque infantil (`infantil: true`).
 */
data class ExploreGroup(
    val title: String?,
    val categories: List<ExploreCategory>,
    val isKids: Boolean = false,
)

/** Ficha completa de un vídeo (`videos/{id}.json`): lo que Findroid muestra en su pantalla de película. */
data class VideoDetail(
    val item: CatalogItem,
    val backdropUrl: String?,
    /** Puede contener HTML; la UI lo renderiza. */
    val description: String?,
    val promo: String?,
    val subtypeName: String?,
    val programTitle: String?,
    val year: String?,
    val ageRating: String?,
    val genres: List<String>,
    val director: String?,
    val cast: List<String>,
    val originalLanguage: String?,
    val expirationDate: String?,
    val webUrl: String?,
)

/** Miniaturas de la barra de progreso: un sprite y las regiones que corresponden a cada tramo. */
data class PreviewSprite(
    val imageUrl: String,
    val cues: List<SpriteCue>,
) {
    fun cueAt(positionMs: Long): SpriteCue? = cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
        ?: cues.lastOrNull()?.takeIf { positionMs >= it.endMs }
}

data class SpriteCue(
    val startMs: Long,
    val endMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
