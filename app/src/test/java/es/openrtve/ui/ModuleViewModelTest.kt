package es.openrtve.ui

import es.openrtve.domain.CatalogModule
import es.openrtve.domain.HomeRow
import es.openrtve.testing.FakeCatalogRepository
import es.openrtve.testing.MainDispatcherRule
import es.openrtve.testing.catalogItem
import es.openrtve.testing.loaded
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val row = HomeRow(id = 3, title = "", order = 3, moduleType = null, presentation = null, contentUrl = "https://api.rtve.es/api/collection/3.json")
    private val repository = FakeCatalogRepository().apply {
        module = { _, _, _ -> loaded(CatalogModule("Cine", listOf(catalogItem("a"), catalogItem("b")))) }
    }

    @Test
    fun `the grid loads from cache first and takes the collection title`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = ModuleViewModel(repository, row, "Fila")
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Cine", state.title)
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertFalse(state.isLoading)
        assertEquals(listOf("module(${row.contentUrl}, force=false, cached=false)"), repository.calls)
    }

    @Test
    fun `refresh keeps the items on screen and forces the network`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = ModuleViewModel(repository, row, "Fila")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.module = { _, _, _ ->
            gate.await()
            loaded(CatalogModule("", listOf(catalogItem("c"))), isStale = true)
        }
        viewModel.refresh()
        runCurrent()
        val refreshing = viewModel.uiState.value
        assertTrue(refreshing.isRefreshing)
        assertFalse(refreshing.isLoading)
        assertEquals(listOf("a", "b"), refreshing.items.map { it.id })
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("c"), state.items.map { it.id })
        assertEquals("sin título nuevo se conserva el anterior", "Cine", state.title)
        assertTrue(state.isStale)
        assertFalse(state.isRefreshing)
        assertTrue(repository.calls.last().contains("force=true"))
    }

    @Test
    fun `errors keep the previous items and are dismissable`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = ModuleViewModel(repository, row, "Fila")
        advanceUntilIdle()
        repository.module = { _, _, _ -> throw UnknownHostException() }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoadError.Offline, state.error)
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertFalse(state.isRefreshing)
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }
}
