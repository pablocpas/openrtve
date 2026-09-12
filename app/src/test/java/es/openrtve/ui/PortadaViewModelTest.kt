package es.openrtve.ui

import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.CatalogLoad
import es.openrtve.domain.CatalogModule
import es.openrtve.domain.CatalogPage
import es.openrtve.domain.ContentKind
import es.openrtve.domain.ExploreGroup
import es.openrtve.domain.HomeFeed
import es.openrtve.domain.HomeRow
import es.openrtve.domain.ProgramDetail
import es.openrtve.domain.QuickFilter
import es.openrtve.domain.SearchResults
import es.openrtve.domain.VideoDetail
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortadaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshing during the first load does not leak a cancellation as an error`() = runTest(dispatcher) {
        val viewModel = PortadaViewModel(repository, URL)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        val first = repository.feed
        repository.feed = CompletableDeferred()
        viewModel.refresh()
        dispatcher.scheduler.runCurrent()
        first.complete(feed("Vieja", rows = 1))
        dispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("la nueva carga sigue en marcha", state.isLoading)
        assertNull(state.error)
        assertEquals("", state.title)

        repository.feed.complete(feed("Nueva", rows = 1))
        repository.modules.getValue(0).complete(module(items = 2))
        dispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value
        assertEquals("Nueva", loaded.title)
        assertEquals(2, (loaded.sections.single().state as SectionState.Loaded).items.size)
    }

    @Test
    fun `sections load independently, take the collection title and empty ones disappear`() = runTest(dispatcher) {
        val viewModel = PortadaViewModel(repository, URL)
        repository.feed.complete(feed("TV", rows = 3))
        dispatcher.scheduler.runCurrent()

        assertEquals(3, viewModel.uiState.value.sections.size)
        assertTrue(viewModel.uiState.value.sections.all { it.state is SectionState.Loading })

        repository.modules.getValue(0).complete(module(items = 1, title = "Lo más destacado"))
        repository.modules.getValue(1).complete(module(items = 0))
        repository.modules.getValue(2).completeExceptionally(IOException("boom"))
        dispatcher.scheduler.advanceUntilIdle()

        val sections = viewModel.uiState.value.sections
        assertEquals(listOf(0, 2), sections.map { it.row.id })
        assertEquals("Lo más destacado", sections[0].title)
        assertEquals(SectionState.Failed(LoadError.Unknown), sections[1].state)
    }

    private fun feed(title: String, rows: Int) = CatalogLoad(
        HomeFeed(
            id = null,
            title = title,
            rows = List(rows) { index ->
                HomeRow(
                    id = index,
                    title = "",
                    order = index,
                    moduleType = null,
                    presentation = null,
                    contentUrl = "https://api.rtve.es/api/collection/$index.json",
                )
            },
        ),
        isStale = false,
    )

    private fun module(items: Int, title: String = "") = CatalogLoad(
        CatalogModule(
            title = title,
            items = List(items) { index ->
                CatalogItem(
                    id = "item-$index",
                    playbackId = "$index",
                    assetId = null,
                    title = "Item $index",
                    subtitle = null,
                    imageUrl = null,
                    kind = ContentKind.VIDEO,
                    directQualityUrl = null,
                    allowedInCountry = null,
                    loginRequired = false,
                    paid = false,
                    drm = false,
                )
            },
        ),
        isStale = false,
    )

    private class FakeRepository : CatalogRepository {
        var feed = CompletableDeferred<CatalogLoad<HomeFeed>>()
        val modules = (0 until 3).associateWith { CompletableDeferred<CatalogLoad<CatalogModule>>() }

        override suspend fun loadPortada(url: String, forceRefresh: Boolean) = feed.await()
        override suspend fun loadModule(row: HomeRow, forceRefresh: Boolean) = modules.getValue(row.id).await()
        override suspend fun loadExplore(forceRefresh: Boolean): CatalogLoad<List<ExploreGroup>> = throw UnsupportedOperationException()
        override suspend fun loadQuickFilters(): CatalogLoad<List<QuickFilter>> = throw UnsupportedOperationException()
        override suspend fun loadQuickFilterItems(filter: QuickFilter): CatalogLoad<CatalogModule> = throw UnsupportedOperationException()
        override suspend fun search(query: String): SearchResults = throw UnsupportedOperationException()
        override suspend fun loadProgram(programId: String, forceRefresh: Boolean): CatalogLoad<ProgramDetail> = throw UnsupportedOperationException()
        override suspend fun loadVideo(videoId: String, forceRefresh: Boolean): CatalogLoad<VideoDetail> = throw UnsupportedOperationException()
        override suspend fun loadProgramVideos(programId: String, seasonId: String?, page: Int, completeOnly: Boolean, forceRefresh: Boolean): CatalogLoad<CatalogPage> = throw UnsupportedOperationException()
    }

    private companion object {
        const val URL = "https://www.rtve.es/play/index_apps.json"
    }
}
