package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.QuickFilter
import es.openrtve.domain.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Contenido del filtro rápido seleccionado: cargando, cargado (quizá vacío) o fallido. */
sealed interface FilterState {
    data object Loading : FilterState
    data class Loaded(val items: List<CatalogItem>) : FilterState
    data class Failed(val error: LoadError) : FilterState
}

data class SearchUiState(
    val query: String = "",
    val filters: List<QuickFilter> = emptyList(),
    val selectedFilter: QuickFilter? = null,
    /** Items del filtro rápido seleccionado (cuando no hay texto). */
    val filter: FilterState = FilterState.Loading,
    /** Resultados del texto escrito (a partir de [MIN_QUERY_LENGTH] caracteres). */
    val results: SearchResults? = null,
    val isSearching: Boolean = false,
    val error: LoadError? = null,
) {
    val isTextSearch: Boolean get() = query.trim().length >= MIN_QUERY_LENGTH
    val filterItems: List<CatalogItem> get() = (filter as? FilterState.Loaded)?.items.orEmpty()

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}

/**
 * Buscador al estilo de la app oficial: campo de texto y, debajo, pestañas de
 * búsquedas predefinidas mientras no se escribe nada.
 */
class SearchViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val filters = repository.loadQuickFilters().value
                mutableUiState.update { it.copy(filters = filters) }
                filters.firstOrNull()?.let(::selectFilter)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.toLoadError()) }
            }
        }
        observeQuery()
    }

    fun setQuery(query: String) {
        mutableUiState.update { it.copy(query = query) }
    }

    fun clearQuery() = setQuery("")

    fun selectFilter(filter: QuickFilter) {
        val current = mutableUiState.value
        if (filter == current.selectedFilter && current.filter is FilterState.Loaded) return
        filterJob?.cancel()
        mutableUiState.update { it.copy(selectedFilter = filter, filter = FilterState.Loading) }
        filterJob = viewModelScope.launch {
            val loaded = try {
                FilterState.Loaded(repository.loadQuickFilterItems(filter).value.items)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // El fallo se pinta en el sitio del filtro, con su reintento; no como aviso suelto.
                FilterState.Failed(error.toLoadError())
            }
            mutableUiState.update { it.copy(filter = loaded) }
        }
    }

    /** Vuelve a pedir el filtro seleccionado tras un fallo. */
    fun retryFilter() {
        val filter = mutableUiState.value.selectedFilter ?: return
        mutableUiState.update { it.copy(filter = FilterState.Loading) }
        selectFilter(filter)
    }

    fun dismissError() {
        mutableUiState.update { it.copy(error = null) }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            mutableUiState
                .map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(QUERY_DEBOUNCE_MS)
                .collect { query ->
                    if (query.length < SearchUiState.MIN_QUERY_LENGTH) {
                        mutableUiState.update { it.copy(results = null, isSearching = false) }
                        return@collect
                    }
                    mutableUiState.update { it.copy(isSearching = true, error = null) }
                    try {
                        val results = repository.search(query)
                        mutableUiState.update { it.copy(results = results, isSearching = false) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mutableUiState.update { it.copy(isSearching = false, error = error.toLoadError()) }
                    }
                }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 400L
    }
}
