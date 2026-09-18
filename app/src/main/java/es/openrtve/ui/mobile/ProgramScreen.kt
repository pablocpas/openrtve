package es.openrtve.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.data.WatchEntry
import es.openrtve.data.WatchHistory
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.LoadMoreOnScrollEnd
import es.openrtve.ui.PlaySuggestion
import es.openrtve.ui.ProgramViewModel
import es.openrtve.ui.rememberProgramScreen
import es.openrtve.ui.shareLink
import es.openrtve.ui.ProgramUiState

/**
 * Ficha de programa: cabecera con imagen y botón grande (Reproducir, Continuar
 * o Siguiente según el historial), temporadas y episodios con su progreso.
 */
@Composable
fun ProgramScreen(
    repository: CatalogRepository,
    history: WatchHistory,
    program: CatalogItem,
    onBack: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onError: (String) -> Unit,
) {
    val screen = rememberProgramScreen(repository, history, program, onError)
    val viewModel = screen.viewModel
    val state = screen.state
    val listState = rememberLazyListState()
    // Paginación infinita: pide la siguiente página al acercarse al final.
    LoadMoreOnScrollEnd(listState, threshold = 4, loadMore = viewModel::loadMore)

    Box(Modifier.fillMaxSize()) {
        ProgramList(state, program, screen.suggestion, screen.entriesById, listState, viewModel, onOpenItem)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
        }
    }
}

@Composable
private fun ProgramList(
    state: ProgramUiState,
    program: CatalogItem,
    suggestion: PlaySuggestion?,
    entriesById: Map<String, WatchEntry>,
    listState: LazyListState,
    viewModel: ProgramViewModel,
    onOpenItem: (CatalogItem) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ProgramHeader(
                state = state,
                program = program,
                suggestion = suggestion,
                scrollOffset = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
                onPlay = onOpenItem,
            )
        }
        if (state.detail?.seasons?.isNotEmpty() == true) {
            item(key = "seasons") { SeasonChips(state, onSelect = viewModel::selectSeason) }
        }
        if (state.showingClips) {
            item(key = "clips-notice") {
                Text(
                    text = stringResource(R.string.program_clips_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
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
                val entry = entriesById[episode.id]
                EpisodeRow(
                    item = episode,
                    onClick = { onOpenItem(episode) },
                    progress = entry?.takeIf { it.inProgress }?.progress,
                    badge = when {
                        entry?.finished == true -> stringResource(R.string.episode_watched)
                        suggestion is PlaySuggestion.Next && suggestion.item.id == episode.id -> stringResource(R.string.episode_next)
                        else -> null
                    },
                    highlighted = suggestion?.item?.id == episode.id,
                )
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
private fun ProgramHeader(
    state: ProgramUiState,
    program: CatalogItem,
    suggestion: PlaySuggestion?,
    scrollOffset: () -> Int,
    onPlay: (CatalogItem) -> Unit,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clipToBounds(),
        ) {
            AsyncImage(
                model = state.detail?.imageUrl ?: program.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = scrollOffset() / 2f }
                    .background(MaterialTheme.colorScheme.surface),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 1f to background)),
            )
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = ScreenPadding),
            )
        }
        Column(Modifier.padding(horizontal = ScreenPadding)) {
            state.detail?.emission?.let {
                Spacer(Modifier.height(6.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { suggestion?.let { onPlay(it.item) } },
                    enabled = suggestion != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (suggestion) {
                            null -> stringResource(R.string.action_play)
                            is PlaySuggestion.Continue -> stringResource(R.string.program_continue_episode, suggestion.item.title)
                            is PlaySuggestion.Next -> stringResource(R.string.program_next_episode, suggestion.item.title)
                            is PlaySuggestion.Play -> stringResource(R.string.program_play_episode, suggestion.item.title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.detail?.webUrl?.let { url ->
                    FilledTonalIconButton(onClick = { context.shareLink(state.title, url) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                }
            }
            (suggestion as? PlaySuggestion.Continue)?.let {
                Spacer(Modifier.height(8.dp))
                ProgressStrip(it.entry.progress, Modifier.padding(horizontal = 2.dp))
            }
            state.detail?.description?.let { html ->
                var expanded by remember { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = remember(html) { AnnotatedString.fromHtml(html) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
            Spacer(Modifier.height(8.dp))
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
            .padding(horizontal = ScreenPadding, vertical = 4.dp),
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
