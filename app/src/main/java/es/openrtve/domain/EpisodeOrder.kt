package es.openrtve.domain

/** Sentido en que se listan los episodios de un programa. */
enum class EpisodeOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    ;

    /** Valor del parámetro `order` de `programas/{id}/videos.json`. */
    val apiValue: String
        get() = when (this) {
            NEWEST_FIRST -> "multimedia_date_emission,desc"
            OLDEST_FIRST -> "multimedia_date_emission,asc"
        }
}

/*
 * Reglas de RTVE Play 8.8.1 (`ProgramaFragmentOrderUtils`, verificadas el
 * 18-09-2026 contra la API): una serie completa se lee del principio; un programa
 * en emisión, del final (salvo sus temporadas pasadas, que se leen del
 * principio); noticias y eventos siempre del final mientras emiten; conciertos,
 * entrevistas, reportajes y documentales siempre del final; el resto (fuera de
 * emisión y sin completar), del principio.
 */

/** Informativos Noticias y Especial Evento. */
private val NEWEST_WHILE_ON_AIR = setOf("132534", "136522")

/** Conciertos, Entrevistas, Reportajes Factual y Documental. */
private val ALWAYS_NEWEST = setOf("136613", "136525", "136521", "137650")

/**
 * Orden de los episodios. [isFirstSeason] es `true` para la lista completa y
 * para la primera pestaña de temporada (en emisión, la temporada en curso).
 */
fun ProgramDetail.episodeOrder(isFirstSeason: Boolean = true): EpisodeOrder = when {
    isComplete -> EpisodeOrder.OLDEST_FIRST
    inEmission && programTypeId in NEWEST_WHILE_ON_AIR -> EpisodeOrder.NEWEST_FIRST
    programTypeId in ALWAYS_NEWEST -> EpisodeOrder.NEWEST_FIRST
    inEmission && isFirstSeason -> EpisodeOrder.NEWEST_FIRST
    else -> EpisodeOrder.OLDEST_FIRST
}

/** Orden de las pestañas de temporada: la más reciente primero salvo en programas terminados sin completar. */
fun ProgramDetail.seasonOrder(): EpisodeOrder = when {
    isComplete || inEmission || programTypeId in ALWAYS_NEWEST -> EpisodeOrder.NEWEST_FIRST
    else -> EpisodeOrder.OLDEST_FIRST
}

/** Temporadas con episodios, en el orden de RTVE Play. */
fun ProgramDetail.seasonsInDisplayOrder(): List<ProgramSeason> {
    val withEpisodes = seasons.filter { it.episodeCount == null || it.episodeCount > 0 }
    return when (seasonOrder()) {
        EpisodeOrder.NEWEST_FIRST -> withEpisodes.sortedByDescending { it.order ?: Int.MIN_VALUE }
        EpisodeOrder.OLDEST_FIRST -> withEpisodes.sortedBy { it.order ?: Int.MAX_VALUE }
    }
}

/** Si [seasonId] es la primera pestaña; sin temporada seleccionada ("Todos") cuenta como tal. */
fun ProgramDetail.isFirstSeason(seasonId: String?): Boolean =
    seasonId == null || seasons.firstOrNull()?.id == seasonId
