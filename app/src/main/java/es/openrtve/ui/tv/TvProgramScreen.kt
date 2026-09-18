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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.data.WatchHistory
import es.openrtve.ui.LoadMoreOnScrollEnd
import es.openrtve.ui.PlaySuggestion
import es.openrtve.ui.rememberProgramScreen
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.metaLine

/**
 * Ficha de programa en TV al estilo de las apps de streaming: fondo a pantalla
 * completa con degradado, información arriba a la izquierda, selector de
 * temporada y una única fila horizontal de episodios abajo.
 */
@Composable
fun TvProgramScreen(
    repository: CatalogRepository,
    history: WatchHistory,
    program: CatalogItem,
    onOpenItem: (CatalogItem) -> Unit,
    onError: (String) -> Unit,
) {
    val screen = rememberProgramScreen(repository, history, program, onError)
    val viewModel = screen.viewModel
    val state = screen.state
    val entriesById = screen.entriesById
    val suggestion = screen.suggestion
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val background = MaterialTheme.colorScheme.background
    LoadMoreOnScrollEnd(listState, threshold = 3, loadMore = viewModel::loadMore)

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
                        text = remember(it) { AnnotatedString.fromHtml(it) },
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
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { suggestion?.let { onOpenItem(it.item) } },
                    enabled = suggestion != null,
                    modifier = Modifier.initialFocus(),
                ) {
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
                            TvChoiceButton(stringResource(R.string.program_all_seasons), state.selectedSeasonId == null) { viewModel.selectSeason(null) }
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
                        items(state.episodes, key = { it.id }) { episode ->
                            val entry = entriesById[episode.id]
                            TvEpisodeCard(
                                item = episode,
                                onClick = { onOpenItem(episode) },
                                modifier = Modifier.width(EpisodeWidth),
                                progress = entry?.takeIf { it.inProgress }?.progress,
                                badge = when {
                                    entry?.finished == true -> stringResource(R.string.episode_watched)
                                    suggestion is PlaySuggestion.Next && suggestion.item.id == episode.id -> stringResource(R.string.episode_next)
                                    else -> null
                                },
                            )
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
private fun TvEpisodeCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier,
    progress: Float? = null,
    badge: String? = null,
) {
    val context = LocalContext.current
    Column(modifier) {
        TvFocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            TvArtwork(item.imageUrl, Modifier.fillMaxSize())
            progress?.let { TvProgressStrip(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Spacer(Modifier.height(10.dp))
        badge?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.metaLine(context)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val EpisodeWidth = 300.dp
