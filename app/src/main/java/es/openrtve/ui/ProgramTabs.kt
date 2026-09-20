package es.openrtve.ui

import es.openrtve.R
import es.openrtve.domain.VideoDetail

/**
 * Pestañas de las fichas, con la misma regla que en LibreTres:
 *
 * - **Cabecera** = lo que hace falta para decidir y darle al play (título, datos,
 *   sinopsis plegada, botón). Nunca en una pestaña.
 * - **Pestañas** = colecciones que se recorren. Sólo se enseña la fila si hay más
 *   de una; con una, su contenido va directamente debajo de la cabecera.
 * - **Detalles** = la cola larga (ficha técnica, reparto): sólo si cuenta algo más
 *   que la cabecera.
 *
 * Hoy la ficha de programa de RTVE sólo tiene capítulos y la de vídeo sólo ficha
 * técnica, así que ninguna enseña la fila. Cuando el catálogo dé clips o
 * recomendados, aparece sola.
 */
enum class ProgramTab {
    EPISODES,
    MORE_VIDEOS,
    DETAILS,
}

/** Pestañas de la ficha de un programa: los capítulos (o fragmentos) y nada más, por ahora. */
fun programTabs(body: ProgramBody): List<ProgramTab> = listOf(ProgramTab.EPISODES).take(MAX_TABS)

/** Pestañas de la ficha de un vídeo: la ficha técnica, si la hay. */
fun videoTabs(state: VideoUiState): List<ProgramTab> =
    listOfNotNull(ProgramTab.DETAILS.takeIf { state.detail?.facts?.hasDetails == true }).take(MAX_TABS)

internal const val MAX_TABS = 4

/** Ficha técnica de un vídeo, en filas etiqueta/valor; la sinopsis va aparte, en la cabecera. */
data class FichaFacts(
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val cast: List<String> = emptyList(),
    val originalLanguage: String? = null,
    val expirationDate: String? = null,
) {
    /** Si la pestaña de detalles tiene algo que enseñar. */
    val hasDetails: Boolean get() = rows.isNotEmpty()

    /** Las filas etiqueta/valor, omitiendo lo que falte. */
    val rows: List<Pair<Int, String>> = buildList {
        genres.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(R.string.video_genres to it) }
        director?.takeIf { it.isNotBlank() }?.let { add(R.string.video_director to it) }
        cast.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(R.string.video_cast to it) }
        originalLanguage?.let(::languageName)?.let { add(R.string.video_language to it) }
        expirationDate?.let { add(R.string.video_available_until to it.feedDateToDisplay()) }
    }
}

val VideoDetail.facts: FichaFacts
    get() = FichaFacts(
        genres = genres,
        director = director,
        cast = cast,
        originalLanguage = originalLanguage,
        expirationDate = expirationDate,
    )

private fun languageName(code: String): String =
    java.util.Locale.forLanguageTag(code)
        .getDisplayLanguage(java.util.Locale.forLanguageTag("es"))
        .replaceFirstChar { it.uppercase() }
