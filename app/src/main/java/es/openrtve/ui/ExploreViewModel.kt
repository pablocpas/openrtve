package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.ExploreGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val groups: List<ExploreGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: LoadError? = null,
)

/** Categorías del menú de la app oficial, leídas de la configuración remota. */
class ExploreViewModel(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = mutableUiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = repository.loadExplore().value
                mutableUiState.update { it.copy(groups = groups, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.toLoadError()) }
            }
        }
    }
}
