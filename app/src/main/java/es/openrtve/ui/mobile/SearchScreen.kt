package es.openrtve.ui.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.RowLayout
import es.openrtve.ui.SearchViewModel
import es.openrtve.ui.text

/** Buscador: campo de texto, filtros rápidos de la app oficial y resultados por bloque. */
@Composable
fun SearchScreen(
    repository: CatalogRepository,
    onOpenItem: (CatalogItem) -> Unit,
    onError: (String) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(repository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
        )
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (state.isTextSearch) {
            SearchResultsView(state.results, onOpenItem)
        } else {
            if (state.filters.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding, vertical = 4.dp),
                ) {
                    state.filters.forEach { filter ->
                        FilterChip(
                            selected = filter == state.selectedFilter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.title) },
                        )
                    }
                }
            }
            when {
                state.selectedFilter == null && state.filters.isEmpty() -> Box(Modifier.fillMaxSize())
                state.filterItems.isEmpty() -> LoadingPanel()
                else -> ItemGrid(state.filterItems, RowLayout.POSTER, onOpenItem)
            }
        }
    }
}

@Composable
private fun SearchResultsView(
    results: es.openrtve.domain.SearchResults?,
    onOpenItem: (CatalogItem) -> Unit,
) {
    when {
        results == null -> Box(Modifier.fillMaxSize())
        results.isEmpty -> EmptyPanel(stringResource(R.string.search_empty))
        else -> LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (results.programs.isNotEmpty()) {
                item(key = "programs-header") { SectionHeader(stringResource(R.string.search_programs)) }
                item(key = "programs") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(results.programs, key = { it.id }) { item ->
                            PosterCard(item, { onOpenItem(item) }, Modifier.width(130.dp))
                        }
                    }
                }
            }
            if (results.videos.isNotEmpty()) {
                item(key = "videos-header") { SectionHeader(stringResource(R.string.search_videos)) }
                items(results.videos, key = { it.id }) { item ->
                    EpisodeRow(item, onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
    )
}
