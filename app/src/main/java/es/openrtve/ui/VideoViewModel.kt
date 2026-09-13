package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.VideoDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoUiState(
    /** Lo que ya sabíamos del item antes de abrir la ficha; permite pintar y reproducir sin esperar. */
    val item: CatalogItem,
    val detail: VideoDetail? = null,
    val isLoading: Boolean = true,
    val error: LoadError? = null,
)

/** Ficha de vídeo o película, al estilo de la pantalla de película de Findroid. */
class VideoViewModel(
    private val repository: CatalogRepository,
    item: CatalogItem,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(VideoUiState(item = item))
    val uiState: StateFlow<VideoUiState> = mutableUiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun dismissError() {
        mutableUiState.update { it.copy(error = null) }
    }

    private fun load() {
        val id = mutableUiState.value.item.playbackId ?: mutableUiState.value.item.id
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = if (mutableUiState.value.item.kind == ContentKind.AUDIO) repository.loadAudio(id).value else repository.loadVideo(id).value
                // La ficha trae los derechos reales, que faltan en muchos feeds de portada.
                mutableUiState.update { it.copy(detail = detail, item = detail.item, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.toLoadError()) }
            }
        }
    }
}
