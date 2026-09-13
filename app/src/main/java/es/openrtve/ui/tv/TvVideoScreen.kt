package es.openrtve.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import es.openrtve.data.WatchHistory
import androidx.compose.runtime.remember
import es.openrtve.domain.CatalogItem
import es.openrtve.ui.VideoViewModel
import es.openrtve.ui.feedDateToDisplay
import es.openrtve.ui.metaLine
import es.openrtve.ui.text

/** Ficha de vídeo/película en TV: fondo a pantalla completa, texto a la izquierda y botones grandes. */
@Composable
fun TvVideoScreen(
    repository: CatalogRepository,
    history: WatchHistory,
    item: CatalogItem,
    onPlay: (CatalogItem, Boolean) -> Unit,
    onOpenProgram: (programId: String, title: String) -> Unit,
    onError: (String) -> Unit,
) {
    val viewModel: VideoViewModel = viewModel(
        key = "video-${item.id}",
        factory = viewModelFactory { initializer { VideoViewModel(repository, item) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val historyEntries by history.entries.collectAsStateWithLifecycle()
    val resume = remember(historyEntries, state.item.id) { history.entryFor(state.item.id)?.takeIf { it.inProgress } }

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }

    val detail = state.detail
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = detail?.backdropUrl ?: state.item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to background, 0.55f to background.copy(alpha = 0.85f), 1f to Color.Transparent)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxSize()
                .padding(TvRowPadding)
                .padding(vertical = TvVerticalMargin),
        ) {
            Text(
                text = state.item.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
            )
            (detail?.programTitle ?: state.item.subtitle)?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            detail?.metaLine(context)?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onPlay(state.item, false) }, modifier = Modifier.initialFocus()) {
                    Text(stringResource(if (resume != null) R.string.action_continue else R.string.action_play))
                }
                if (resume != null) {
                    Button(onClick = { onPlay(state.item, true) }) { Text(stringResource(R.string.action_restart)) }
                }
                state.item.programId?.let { programId ->
                    Button(onClick = { onOpenProgram(programId, detail?.programTitle ?: state.item.subtitle.orEmpty()) }) {
                        Text(stringResource(R.string.action_open_program))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            detail?.description?.let {
                Text(
                    text = AnnotatedString.fromHtml(it),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            }
            detail?.let { d ->
                TvInfoLine(stringResource(R.string.video_genres), d.genres.joinToString(", ").ifBlank { null })
                TvInfoLine(stringResource(R.string.video_director), d.director)
                TvInfoLine(stringResource(R.string.video_cast), d.cast.joinToString(", ").ifBlank { null })
                TvInfoLine(stringResource(R.string.video_available_until), d.expirationDate?.feedDateToDisplay())
            }
        }
    }
}

@Composable
private fun TvInfoLine(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(160.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
