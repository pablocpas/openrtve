package es.openrtve.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.HomeRow
import es.openrtve.domain.RowLayout
import es.openrtve.ui.ModuleViewModel
import es.openrtve.ui.text

/** Rejilla completa de una fila ("Ver todo"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(
    repository: CatalogRepository,
    row: HomeRow,
    title: String,
    onBack: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onError: (String) -> Unit,
) {
    val viewModel: ModuleViewModel = viewModel(
        key = "module-${row.contentUrl}",
        factory = viewModelFactory { initializer { ModuleViewModel(repository, row, title) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = state.title, onBack = onBack)
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.isStale) StaleBanner()
                when {
                    state.isLoading -> LoadingPanel()
                    state.items.isEmpty() -> EmptyPanel(stringResource(R.string.state_empty_module), viewModel::refresh)
                    else -> ItemGrid(state.items, row.layout, onOpenItem)
                }
            }
        }
    }
}

@Composable
internal fun ItemGrid(items: List<CatalogItem>, layout: RowLayout, onOpenItem: (CatalogItem) -> Unit) {
    val poster = layout == RowLayout.POSTER
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (poster) 110.dp else 160.dp),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            when {
                poster -> PosterCard(item, { onOpenItem(item) })
                layout == RowLayout.SQUARE -> SquareCard(item, { onOpenItem(item) })
                else -> ItemCard(item, { onOpenItem(item) })
            }
        }
    }
}
