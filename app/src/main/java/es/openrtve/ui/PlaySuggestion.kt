package es.openrtve.ui

import es.openrtve.data.WatchEntry
import es.openrtve.data.WatchHistory
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.EpisodeOrder

/** Qué debe hacer el botón grande de una ficha de programa. */
sealed interface PlaySuggestion {
    val item: CatalogItem

    data class Play(override val item: CatalogItem) : PlaySuggestion
    data class Continue(override val item: CatalogItem, val entry: WatchEntry) : PlaySuggestion
    data class Next(override val item: CatalogItem) : PlaySuggestion
}

/**
 * Sin historial: el primero de la lista (el más reciente, o el primer capítulo
 * si la serie se lee del principio). A medias: continuar. Terminado: el
 * siguiente cronológico; si no está en la lista, el más reciente cuando hay uno
 * más nuevo que el visto, o el primero de la lista. [order] dice hacia dónde va.
 */
fun suggestPlay(
    history: WatchHistory,
    programId: String,
    episodes: List<CatalogItem>,
    order: EpisodeOrder = EpisodeOrder.NEWEST_FIRST,
): PlaySuggestion? {
    val last = history.lastWatchedOf(programId)
    val start = episodes.firstOrNull()
    val newestFirst = order == EpisodeOrder.NEWEST_FIRST
    return when {
        last == null -> start?.let { PlaySuggestion.Play(it) }
        last.inProgress -> PlaySuggestion.Continue(last.item, last)
        else -> {
            val index = episodes.indexOfFirst { it.id == last.item.id }
            val following = if (index < 0) -1 else if (newestFirst) index - 1 else index + 1
            when {
                following in episodes.indices -> PlaySuggestion.Next(episodes[following])
                newestFirst && start != null && start.id != last.item.id -> PlaySuggestion.Next(start)
                else -> start?.let { PlaySuggestion.Play(it) }
            }
        }
    }
}
