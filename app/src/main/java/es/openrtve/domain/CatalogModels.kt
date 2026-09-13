package es.openrtve.domain

object RtveUrls {
    const val TV_HOME = "https://www.rtve.es/play/index_apps.json"
    const val RADIO_HOME = "https://www.rtve.es/play/radio/index_apps.json"
    const val REMOTE_CONFIG = "https://www.rtve.es/m/configs/rtve_play/2026/estructura2.json"
    const val QUICK_SEARCHES = "https://www.rtve.es/m/configs/buscador/predefinidos.json"
    const val SEARCH = "https://api.rtve.es/api/search/results"
}

/** Cómo pinta la UI una fila; se deduce del `tipo` editorial y nunca es un enum cerrado en el feed. */
enum class RowLayout {
    /** Carrusel a ancho completo, un item por página ("Slide > Home"). */
    HERO,
    /** Pósters verticales 2:3. */
    POSTER,
    /** Tarjetas cuadradas (radio, música). */
    SQUARE,
    /** Tarjetas apaisadas 16:9. */
    LANDSCAPE,
}

data class HomeFeed(
    val id: String?,
    val title: String,
    val rows: List<HomeRow>,
)

data class HomeRow(
    /** Posición estable dentro de la portada; los títulos y `orden` pueden repetirse. */
    val id: Int,
    val title: String,
    val order: Int,
    val moduleType: String?,
    val presentation: String?,
    val contentUrl: String?,
    val layout: RowLayout = RowLayout.LANDSCAPE,
) {
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

data class ExploreCategory(
    val title: String,
    val imageUrl: String?,
    val portadaUrl: String,
)

data class ExploreGroup(
    val title: String,
    val categories: List<ExploreCategory>,
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
