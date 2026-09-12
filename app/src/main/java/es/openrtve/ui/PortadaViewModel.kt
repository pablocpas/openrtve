package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.HomeRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface SectionState {
    data object Loading : SectionState
    data class Loaded(val items: List<CatalogItem>, val isStale: Boolean) : SectionState
    data class Failed(val error: LoadError) : SectionState
}

data class HomeSection(
    val row: HomeRow,
    val state: SectionState,
    /** Título de la fila o, si la portada no lo trae, el de la propia colección. */
    val title: String = row.title,
)

data class PortadaUiState(
    val title: String = "",
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFeedStale: Boolean = false,
    val error: LoadError? = null,
) {
    val isStale: Boolean
        get() = isFeedStale || sections.any { (it.state as? SectionState.Loaded)?.isStale == true }
}

/**
 * Una portada (`index_apps.json`): la principal, una temática o la de radio.
 * Cada fila se carga por separado para que una lenta no bloquee al resto.
 */
class PortadaViewModel(
    private val repository: CatalogRepository,
    private val url: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PortadaUiState())
    val uiState: StateFlow<PortadaUiState> = mutableUiState.asStateFlow()

    private var feedJob: Job? = null
    /** Ámbito de las cargas de sección del feed actual; un refresh las cancela en bloque. */
    private var sectionsScope: CoroutineScope? = null
    private val sectionLoads = Semaphore(MAX_PARALLEL_SECTION_LOADS)

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    fun dismissError() {
        mutableUiState.update { it.copy(error = null) }
    }

    fun retrySection(row: HomeRow) {
        val alreadyLoading = mutableUiState.value.sections
            .any { it.row.id == row.id && it.state is SectionState.Loading }
        if (alreadyLoading) return
        val scope = sectionsScope ?: return
        updateSection(row.id, SectionState.Loading)
        scope.launch { loadSection(row, forceRefresh = true) }
    }

    private fun load(forceRefresh: Boolean) {
        feedJob?.cancel()
        sectionsScope?.cancel()
        val scope = CoroutineScope(
            viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]),
        ).also { sectionsScope = it }
        feedJob = viewModelScope.launch {
            val hadSections = mutableUiState.value.sections.isNotEmpty()
            mutableUiState.update { it.copy(isLoading = !hadSections, isRefreshing = hadSections, error = null) }
            val feed = try {
                repository.loadPortada(url, forceRefresh)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = error.toLoadError())
                }
                return@launch
            }

            val rows = feed.value.rows.filter { it.contentUrl != null }
            mutableUiState.update {
                it.copy(
                    title = feed.value.title.trim(),
                    sections = rows.map { row -> HomeSection(row, SectionState.Loading) },
                    isLoading = false,
                    isRefreshing = false,
                    isFeedStale = feed.isStale,
                )
            }
            rows.forEach { row -> scope.launch { loadSection(row, forceRefresh) } }
        }
    }

    private suspend fun loadSection(row: HomeRow, forceRefresh: Boolean) {
        var title: String? = null
        val state = sectionLoads.withPermit {
            try {
                val module = repository.loadModule(row, forceRefresh)
                title = module.value.title
                SectionState.Loaded(module.value.items, module.isStale)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SectionState.Failed(error.toLoadError())
            }
        }
        // Como en Findroid, una sección vacía desaparece en vez de mostrar un hueco.
        if (state is SectionState.Loaded && state.items.isEmpty()) {
            mutableUiState.update { ui -> ui.copy(sections = ui.sections.filterNot { it.row.id == row.id }) }
        } else {
            updateSection(row.id, state, title)
        }
    }

    private fun updateSection(rowId: Int, state: SectionState, title: String? = null) {
        mutableUiState.update { ui ->
            ui.copy(
                sections = ui.sections.map { section ->
                    if (section.row.id != rowId) {
                        section
                    } else {
                        section.copy(state = state, title = title?.ifBlank { null } ?: section.title)
                    }
                },
            )
        }
    }

    private companion object {
        const val MAX_PARALLEL_SECTION_LOADS = 6
    }
}
