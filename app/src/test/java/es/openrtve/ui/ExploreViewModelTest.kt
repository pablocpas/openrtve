package es.openrtve.ui

import es.openrtve.domain.ExploreCategory
import es.openrtve.domain.ExploreGroup
import es.openrtve.testing.FakeCatalogRepository
import es.openrtve.testing.MainDispatcherRule
import es.openrtve.testing.loaded
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeCatalogRepository()

    @Test
    fun `groups load and a failure can be retried`() = runTest(mainDispatcher.dispatcher) {
        repository.explore = { throw UnknownHostException() }
        val viewModel = ExploreViewModel(repository)
        advanceUntilIdle()
        assertEquals(LoadError.Offline, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        val groups = listOf(ExploreGroup("Series", listOf(ExploreCategory("Drama", null, "https://www.rtve.es/play/drama.json"))))
        repository.explore = { loaded(groups) }
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(groups, viewModel.uiState.value.groups)
    }
}
