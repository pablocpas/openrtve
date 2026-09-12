package es.openrtve.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.ProgramViewModel
import es.openrtve.ui.metaLine
import es.openrtve.ui.text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Ficha de programa en TV al estilo de las apps de streaming: fondo a pantalla
 * completa con degradado, información arriba a la izquierda, selector de
 * temporada y una única fila horizontal de episodios abajo.
 */
@Composable
fun TvProgramScreen(
    repository: CatalogRepository,
    program: CatalogItem,
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
    val background = MaterialTheme.colorScheme.background

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = state.detail?.imageUrl ?: program.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to background, 0.5f to background.copy(alpha = 0.9f), 1f to background.copy(alpha = 0.3f))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to background)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = TvVerticalMargin, bottom = TvVerticalMargin),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .padding(TvRowPadding),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.detail?.emission?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                state.detail?.description?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = AnnotatedString.fromHtml(it),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.showingClips) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.program_clips_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column {
                val seasons = state.detail?.seasons.orEmpty()
                if (seasons.isNotEmpty()) {
                    LazyRow(
                        contentPadding = TvRowPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        item(key = "all") {
                            Box(Modifier.initialFocus()) {
                                TvChoiceButton(stringResource(R.string.program_all_seasons), state.selectedSeasonId == null) { viewModel.selectSeason(null) }
                            }
                        }
                        items(seasons, key = { it.id }) { season ->
                            TvChoiceButton(season.title, state.selectedSeasonId == season.id) { viewModel.selectSeason(season.id) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                when {
                    state.isLoading -> Text(
                        stringResource(R.string.state_loading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(TvRowPadding),
                    )
                    state.episodes.isEmpty() -> Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(TvRowPadding),
                    ) {
                        Text(stringResource(R.string.program_empty), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                    }
                    else -> LazyRow(
                        state = listState,
                        contentPadding = TvRowPadding,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(state.episodes, key = { _, it -> it.id }) { index, episode ->
                            val focus = if (index == 0 && seasons.isEmpty()) Modifier.initialFocus() else Modifier
                            TvEpisodeCard(episode, { onOpenItem(episode) }, focus.width(EpisodeWidth))
                        }
                        if (state.hasMore) {
                            item(key = "more") {
                                Column(Modifier.width(EpisodeWidth)) {
                                    TvFocusSurface(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                        Text(
                                            stringResource(if (state.isLoadingMore) R.string.state_loading else R.string.program_load_more),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeCard(item: CatalogItem, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        TvFocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.metaLine(context)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val EpisodeWidth = 300.dp
