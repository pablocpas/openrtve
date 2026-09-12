package es.openrtve.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.ExploreCategory
import es.openrtve.ui.ExploreViewModel
import es.openrtve.ui.exploreGroupTitle
import es.openrtve.ui.text
import androidx.compose.ui.res.stringResource

@Composable
fun TvExploreScreen(
    repository: CatalogRepository,
    onOpenCategory: (ExploreCategory) -> Unit,
) {
    val viewModel: ExploreViewModel = viewModel(
        factory = viewModelFactory { initializer { ExploreViewModel(repository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TvScreenTitle(stringResource(R.string.tab_explore))
        when {
        state.isLoading -> TvCenterMessage(stringResource(R.string.state_loading))
        state.error != null && state.groups.isEmpty() -> TvCenterMessage(state.error!!.text(context), viewModel::retry)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            contentPadding = PaddingValues(start = TvHorizontalMargin, end = TvHorizontalMargin, bottom = TvVerticalMargin),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.groups.forEach { group ->
                item(key = "group-${group.title}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = exploreGroupTitle(context, group.title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                itemsIndexed(group.categories, key = { _, it -> it.portadaUrl }) { index, category ->
                    val focus = if (index == 0 && group == state.groups.first()) Modifier.initialFocus() else Modifier
                    TvImageTile(category.title, category.imageUrl, { onOpenCategory(category) }, focus.fillMaxWidth())
                }
            }
        }
        }
    }
}
