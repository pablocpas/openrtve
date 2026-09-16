package es.openrtve.ui

import es.openrtve.data.HttpStatusException
import es.openrtve.domain.ContentKind
import es.openrtve.domain.VideoDetail
import es.openrtve.testing.FakeCatalogRepository
import es.openrtve.testing.MainDispatcherRule
import es.openrtve.testing.catalogItem
import es.openrtve.testing.loaded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeCatalogRepository().apply {
        video = { id, _ -> loaded(detail(catalogItem(id).copy(drm = true, title = "Ficha $id"))) }
        audio = { id, _ -> loaded(detail(catalogItem(id, kind = ContentKind.AUDIO))) }
    }

    @Test
    fun `the sheet replaces the row item because it carries the real rights`() = runTest(mainDispatcher.dispatcher) {
        val fromRow = catalogItem("1").copy(title = "De la fila")
        val viewModel = VideoViewModel(repository, fromRow)
        assertEquals("se pinta lo que ya se sabía", "De la fila", viewModel.uiState.value.item.title)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Ficha 1", state.item.title)
        assertTrue(state.item.drm)
        assertFalse(state.isLoading)
    }

    @Test
    fun `programs open their last episode and audios use the audio sheet`() = runTest(mainDispatcher.dispatcher) {
        val program = catalogItem("p1", kind = ContentKind.PROGRAM, playbackId = "ep9")
        VideoViewModel(repository, program)
        VideoViewModel(repository, catalogItem("a1", kind = ContentKind.AUDIO))
        advanceUntilIdle()

        assertEquals(listOf("video(ep9, force=false)", "audio(a1, force=false)"), repository.calls)
    }

    @Test
    fun `errors keep the known item and retry reloads`() = runTest(mainDispatcher.dispatcher) {
        repository.video = { _, _ -> throw HttpStatusException(404) }
        val viewModel = VideoViewModel(repository, catalogItem("1"))
        advanceUntilIdle()
        assertEquals(LoadError.Http(404), viewModel.uiState.value.error)
        assertEquals("1", viewModel.uiState.value.item.id)
        assertNull(viewModel.uiState.value.detail)

        repository.video = { id, _ -> loaded(detail(catalogItem(id))) }
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals("1", viewModel.uiState.value.detail!!.item.id)
    }

    private fun detail(item: es.openrtve.domain.CatalogItem) = VideoDetail(
        item = item, backdropUrl = null, description = null, promo = null, subtypeName = null, programTitle = null,
        year = null, ageRating = null, genres = emptyList(), director = null, cast = emptyList(), originalLanguage = null,
        expirationDate = null, webUrl = null,
    )
}
