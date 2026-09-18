package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.EpisodeOrder
import es.openrtve.domain.episodeOrder
import es.openrtve.domain.isFirstSeason
import es.openrtve.domain.ProgramDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramUiState(
    val programId: String,
    val title: String,
    val detail: ProgramDetail? = null,
    val selectedSeasonId: String? = null,
    val episodes: List<CatalogItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    /** El programa no tiene contenidos completos y se muestran sus fragmentos. */
    val showingClips: Boolean = false,
    /** Sentido de [episodes]: el de RTVE Play para este programa y temporada. */
    val order: EpisodeOrder = EpisodeOrder.NEWEST_FIRST,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isStale: Boolean = false,
    val error: LoadError? = null,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** Ficha de programa: detalle + episodios paginados, al estilo de la pantalla de serie de Findroid. */
class ProgramViewModel(
    private val repository: CatalogRepository,
    programId: String,
    initialTitle: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProgramUiState(programId = programId, title = initialTitle))
    val uiState: StateFlow<ProgramUiState> = mutableUiState.asStateFlow()
    private var episodesJob: Job? = null

    init {
        viewModelScope.launch {
            // La ficha decide de dónde salen los episodios (radio -> audios.json), así que va primero.
            try {
                val detail = repository.loadProgram(programId)
                mutableUiState.update {
                    it.copy(
                        detail = detail.value,
                        title = detail.value.title.ifBlank { it.title },
                        isStale = it.isStale || detail.isStale,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Sin ficha se intenta igualmente con los vídeos.
            }
            loadFirstPage()
        }
    }

    private val isRadio: Boolean get() = mutableUiState.value.detail?.isRadio == true

    /** De dónde salió la primera página; las siguientes van a la misma fuente aunque el programa sea de radio. */
    private var episodesFromAudios = false

    private suspend fun loadPage(programId: String, seasonId: String?, page: Int, completeOnly: Boolean, order: EpisodeOrder, fromAudios: Boolean) =
        if (fromAudios) repository.loadProgramAudios(programId, page) else repository.loadProgramVideos(programId, seasonId, page, completeOnly, order)

    /** Sin ficha no hay regla: del más nuevo al más antiguo, como los feeds. */
    private fun orderFor(seasonId: String?): EpisodeOrder =
        mutableUiState.value.detail?.let { it.episodeOrder(isFirstSeason = it.isFirstSeason(seasonId)) } ?: EpisodeOrder.NEWEST_FIRST

    fun selectSeason(seasonId: String?) {
        if (seasonId == mutableUiState.value.selectedSeasonId) return
        mutableUiState.update { it.copy(selectedSeasonId = seasonId, episodes = emptyList(), page = 0, totalPages = 0) }
        loadFirstPage()
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (!state.hasMore || state.isLoadingMore || state.isLoading) return
        episodesJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = loadPage(state.programId, state.selectedSeasonId, state.page + 1, completeOnly = !state.showingClips, order = state.order, fromAudios = episodesFromAudios)
                mutableUiState.update {
                    it.copy(
                        episodes = (it.episodes + result.value.items).distinctBy(CatalogItem::id),
                        page = result.value.page,
                        totalPages = result.value.totalPages,
                        isLoadingMore = false,
                        isStale = it.isStale || result.isStale,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoadingMore = false, error = error.toLoadError()) }
            }
        }
    }

    fun retry() {
        mutableUiState.update { it.copy(error = null) }
        if (mutableUiState.value.episodes.isEmpty()) loadFirstPage() else loadMore()
    }

    fun dismissError() {
        mutableUiState.update { it.copy(error = null) }
    }

    private fun loadFirstPage() {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, error = null) }
            val programId = mutableUiState.value.programId
            val seasonId = mutableUiState.value.selectedSeasonId
            val order = orderFor(seasonId)
            try {
                var showingClips = false
                var fromAudios = isRadio
                var result = loadPage(programId, seasonId, page = 1, completeOnly = true, order = order, fromAudios = fromAudios)
                if (result.value.items.isEmpty() && !isRadio) {
                    // Programas de clips (titulares, deportes...) no tienen "Completo".
                    result = repository.loadProgramVideos(programId, seasonId, page = 1, completeOnly = false, order = order)
                    showingClips = result.value.items.isNotEmpty()
                }
                if (result.value.items.isEmpty() && isRadio) {
                    // Algún programa de radio publica videopódcasts en vez de audios.
                    result = repository.loadProgramVideos(programId, seasonId, page = 1, completeOnly = false, order = order)
                    fromAudios = false
                }
                episodesFromAudios = fromAudios
                mutableUiState.update {
                    it.copy(
                        episodes = result.value.items,
                        page = result.value.page,
                        totalPages = result.value.totalPages,
                        showingClips = showingClips,
                        order = order,
                        isLoading = false,
                        isStale = it.isStale || result.isStale,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.toLoadError()) }
            }
        }
    }
}
