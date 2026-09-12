package es.openrtve.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.ProgramUiState
import es.openrtve.ui.ProgramViewModel
import es.openrtve.ui.text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Ficha de programa con temporadas y episodios paginados. */
@Composable
fun ProgramScreen(
    repository: CatalogRepository,
    program: CatalogItem,
    onBack: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onError: (String) -> Unit,
) {
    val viewModel: ProgramViewModel = viewModel(
        key = "program-${program.id}",
        factory = viewModelFactory { initializer { ProgramViewModel(repository, program.id, program.title) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }

    // Paginación infinita: pide la siguiente página al acercarse al final.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = state.title, onBack = onBack)
        ProgramList(state, program, listState, viewModel, onOpenItem)
    }
}

@Composable
private fun ProgramList(
    state: ProgramUiState,
    program: CatalogItem,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: ProgramViewModel,
    onOpenItem: (CatalogItem) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { ProgramHeader(state, program) }
        if (state.detail?.seasons?.isNotEmpty() == true) {
            item(key = "seasons") {
                SeasonChips(state, onSelect = viewModel::selectSeason)
            }
        }
        if (state.showingClips) {
            item(key = "clips-notice") {
                Text(
                    text = stringResource(R.string.program_clips_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        when {
            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.episodes.isEmpty() -> item(key = "empty") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                ) {
                    Text(stringResource(R.string.program_empty))
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                }
            }
            else -> items(state.episodes, key = { it.id }) { episode ->
                EpisodeRow(item = episode, onClick = { onOpenItem(episode) })
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.hasMore && !state.isLoading) {
            item(key = "load-more") {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::loadMore) { Text(stringResource(R.string.program_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun ProgramHeader(state: ProgramUiState, program: CatalogItem) {
    val image = state.detail?.imageUrl ?: program.imageUrl
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            state.detail?.emission?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.detail?.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = AnnotatedString.fromHtml(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SeasonChips(state: ProgramUiState, onSelect: (String?) -> Unit) {
    val seasons = state.detail?.seasons.orEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = state.selectedSeasonId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.program_all_seasons)) },
        )
        seasons.forEach { season ->
            FilterChip(
                selected = state.selectedSeasonId == season.id,
                onClick = { onSelect(season.id) },
                label = { Text(season.title) },
            )
        }
    }
}
