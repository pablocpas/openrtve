package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.data.HttpStatusException
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ProgramDetail
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

    private suspend fun loadPage(programId: String, seasonId: String?, page: Int, completeOnly: Boolean) =
        if (isRadio) repository.loadProgramAudios(programId, page) else repository.loadProgramVideos(programId, seasonId, page, completeOnly)

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
                val result = loadPage(state.programId, state.selectedSeasonId, state.page + 1, completeOnly = !state.showingClips)
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
            try {
                var showingClips = false
                var result = loadPage(programId, seasonId, page = 1, completeOnly = true)
                if (result.value.items.isEmpty() && !isRadio) {
                    // Programas de clips (titulares, deportes...) no tienen "Completo".
                    result = repository.loadProgramVideos(programId, seasonId, page = 1, completeOnly = false)
                    showingClips = result.value.items.isNotEmpty()
                }
                if (result.value.items.isEmpty() && isRadio) {
                    // Algún programa de radio publica videopódcasts en vez de audios.
                    result = repository.loadProgramVideos(programId, seasonId, page = 1, completeOnly = false)
                }
                mutableUiState.update {
                    it.copy(
                        episodes = result.value.items,
                        page = result.value.page,
                        totalPages = result.value.totalPages,
                        showingClips = showingClips,
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

    private fun Throwable.toLoadError(): LoadError = when (this) {
        is UnknownHostException -> LoadError.Offline
        is SocketTimeoutException -> LoadError.Timeout
        is HttpStatusException -> LoadError.Http(statusCode)
        else -> LoadError.Unknown
    }
}
