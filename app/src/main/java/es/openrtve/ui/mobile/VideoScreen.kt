package es.openrtve.ui.mobile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.data.WatchHistory
import androidx.compose.ui.draw.clip
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.VideoDetail
import es.openrtve.ui.VideoViewModel
import es.openrtve.ui.feedDateToDisplay
import es.openrtve.ui.metaLine
import es.openrtve.ui.text

/**
 * Ficha de vídeo/película como la `MovieScreen` de Findroid: fondo con paralaje y
 * degradado, título encima, metadatos, botón de reproducir y ficha técnica.
 */
@Composable
fun VideoScreen(
    repository: CatalogRepository,
    history: WatchHistory,
    item: CatalogItem,
    onBack: () -> Unit,
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
    val scrollState = rememberScrollState()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Backdrop(
                url = detail?.backdropUrl ?: state.item.imageUrl,
                scrollOffset = { scrollState.value },
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = ScreenPadding),
                ) {
                    Text(
                        text = state.item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (detail?.programTitle ?: state.item.subtitle)?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.padding(horizontal = ScreenPadding)) {
                Spacer(Modifier.height(8.dp))
                detail?.metaLine(context)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                resume?.let {
                    ProgressStrip(it.progress, Modifier.clip(CardShape))
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { onPlay(state.item, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (resume != null) R.string.action_continue else R.string.action_play))
                    }
                    if (resume != null) {
                        FilledTonalIconButton(onClick = { onPlay(state.item, true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_restart))
                        }
                    }
                    state.item.programId?.let { programId ->
                        FilledTonalIconButton(onClick = { onOpenProgram(programId, detail?.programTitle ?: state.item.subtitle.orEmpty()) }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.action_open_program))
                        }
                    }
                    detail?.webUrl?.let { url ->
                        FilledTonalIconButton(onClick = { context.share(state.item.title, url) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                when {
                    detail != null -> DetailBody(detail)
                    state.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
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
private fun Backdrop(
    url: String?,
    scrollOffset: () -> Int,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BackdropHeight)
            .clipToBounds(),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
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
        content()
    }
}

@Composable
private fun DetailBody(detail: VideoDetail) {
    val context = LocalContext.current
    Column {
        detail.description?.let { html ->
            var expanded by remember { mutableStateOf(false) }
            Text(
                text = AnnotatedString.fromHtml(html),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Text(
                text = stringResource(if (expanded) R.string.action_less else R.string.action_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        InfoLine(stringResource(R.string.video_genres), detail.genres.joinToString(", ").ifBlank { null })
        InfoLine(stringResource(R.string.video_director), detail.director)
        InfoLine(stringResource(R.string.video_cast), detail.cast.joinToString(", ").ifBlank { null })
        InfoLine(stringResource(R.string.video_language), detail.originalLanguage?.let { languageName(it) })
        InfoLine(stringResource(R.string.video_available_until), detail.expirationDate?.feedDateToDisplay())
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun languageName(code: String): String =
    java.util.Locale(code).getDisplayLanguage(java.util.Locale("es")).replaceFirstChar { it.uppercase() }

private fun android.content.Context.share(title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    startActivity(Intent.createChooser(intent, title))
}

private val BackdropHeight = 288.dp
