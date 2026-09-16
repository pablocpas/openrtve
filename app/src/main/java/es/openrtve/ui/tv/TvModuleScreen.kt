package es.openrtve.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import es.openrtve.ui.rememberCaptionSpec

@Composable
fun TvModuleScreen(
    repository: CatalogRepository,
    row: HomeRow,
    title: String,
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
        TvScreenTitle(state.title)
        when {
            state.isLoading -> TvCenterMessage(stringResource(R.string.state_loading))
            state.items.isEmpty() -> TvCenterMessage(stringResource(R.string.state_empty_module), viewModel::refresh)
            else -> TvItemGrid(state.items, row.layout, onOpenItem)
        }
    }
}

@Composable
internal fun TvItemGrid(items: List<CatalogItem>, layout: RowLayout, onOpenItem: (CatalogItem) -> Unit) {
    // En rejilla el hero no tiene sentido: se degrada a apaisada.
    val gridLayout = if (layout == RowLayout.HERO) RowLayout.LANDSCAPE else layout
    val caption = rememberCaptionSpec(items, gridLayout.cardWidth(), TvCardTitleStyle, gridLayout.maxTitleLines())
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridLayout.cardWidth()),
        contentPadding = PaddingValues(start = TvHorizontalMargin, end = TvHorizontalMargin, bottom = TvVerticalMargin),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            TvItemCard(item, gridLayout, { onOpenItem(item) }, if (index == 0) Modifier.initialFocus() else Modifier, caption = caption)
        }
    }
}
