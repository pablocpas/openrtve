package es.openrtve.ui

import es.openrtve.data.WatchEntry
import es.openrtve.data.WatchHistory
import es.openrtve.domain.CatalogItem

/** Qué debe hacer el botón grande de una ficha de programa. */
sealed interface PlaySuggestion {
    val item: CatalogItem

    data class Play(override val item: CatalogItem) : PlaySuggestion
    data class Continue(override val item: CatalogItem, val entry: WatchEntry) : PlaySuggestion
    data class Next(override val item: CatalogItem) : PlaySuggestion
}

/**
 * Sin historial: el episodio más reciente. A medias: continuar. Terminado: el
 * siguiente en la lista (que va de más nuevo a más antiguo), o el más reciente si
 * el terminado era el último conocido.
 */
fun suggestPlay(history: WatchHistory, programId: String, episodes: List<CatalogItem>): PlaySuggestion? {
    val last = history.lastWatchedOf(programId)
    val newest = episodes.firstOrNull()
    return when {
        last == null -> newest?.let { PlaySuggestion.Play(it) }
        last.inProgress -> PlaySuggestion.Continue(last.item, last)
        else -> {
            val index = episodes.indexOfFirst { it.id == last.item.id }
            when {
                index > 0 -> PlaySuggestion.Next(episodes[index - 1])
                newest != null && newest.id != last.item.id -> PlaySuggestion.Next(newest)
                else -> newest?.let { PlaySuggestion.Play(it) }
            }
        }
    }
}
