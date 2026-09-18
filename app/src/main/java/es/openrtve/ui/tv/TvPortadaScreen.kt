package es.openrtve.ui.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import es.openrtve.R
import es.openrtve.data.CatalogRepository
import es.openrtve.data.WatchEntry
import es.openrtve.data.WatchHistory
import es.openrtve.ui.LocalNowMillis
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.HomeLink
import es.openrtve.domain.HomeRow
import es.openrtve.domain.RowLayout
import es.openrtve.ui.HomeSection
import es.openrtve.ui.SectionState
import es.openrtve.ui.rememberPortadaScreen
import es.openrtve.ui.text

/**
 * Portada de TV como en JetStream: carrusel destacado arriba y filas horizontales
 * con foco recuperable. El foco inicial va a la primera fila.
 */
@Composable
fun TvPortadaScreen(
    repository: CatalogRepository,
    url: String,
    title: String,
    onOpenItem: (CatalogItem) -> Unit,
    onOpenRow: (HomeRow, String) -> Unit,
    onError: (String) -> Unit,
    history: WatchHistory? = null,
    onOpenLink: (HomeLink) -> Unit = {},
) {
    val screen = rememberPortadaScreen(repository, url, history, onError)
    val viewModel = screen.viewModel
    val state = screen.state
    val resumable = screen.resumable
    val firstRowFocus = remember { FocusRequester() }
    val firstLoadedId = state.sections.firstOrNull { it.state is SectionState.Loaded || it.state is SectionState.Links }?.row?.id
    LaunchedEffect(firstLoadedId) {
        if (firstLoadedId != null) runCatching { firstRowFocus.requestFocus() }
    }

    when {
        state.isLoading -> TvCenterMessage(stringResource(R.string.state_loading))
        state.sections.isEmpty() -> TvCenterMessage(stringResource(R.string.state_empty_home), viewModel::refresh)
        else -> LazyColumn(
            contentPadding = PaddingValues(bottom = TvVerticalMargin),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (title.isNotBlank()) {
                item(key = "title") { TvScreenTitle(title) }
            } else {
                item(key = "top") { Spacer(Modifier.height(TvVerticalMargin)) }
            }
            val firstId = state.sections.first().row.id
            items(state.sections, key = { it.row.id }) { section ->
                if (resumable.isNotEmpty() && section.row.id == firstId && section.row.layout != RowLayout.HERO) {
                    TvContinueWatching(resumable, onOpenItem)
                    Spacer(Modifier.height(28.dp))
                }
                TvSection(
                    section = section,
                    // Como en Findroid: las filas se recolocan con animación si otra cambia de alto.
                    modifier = Modifier.animateItem().then(if (section.row.id == firstLoadedId) Modifier.focusRequester(firstRowFocus) else Modifier),
                    onOpenItem = onOpenItem,
                    onOpenLink = onOpenLink,
                    onSeeAll = { onOpenRow(section.row, section.title) },
                    onRetry = { viewModel.retrySection(section.row) },
                )
                if (resumable.isNotEmpty() && section.row.id == firstId && section.row.layout == RowLayout.HERO) {
                    Spacer(Modifier.height(28.dp))
                    TvContinueWatching(resumable, onOpenItem)
                }
            }
        }
    }
}

@Composable
private fun TvContinueWatching(entries: List<WatchEntry>, onOpenItem: (CatalogItem) -> Unit) {
    Column {
        TvSectionTitle(stringResource(R.string.continue_watching))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = TvRowPadding,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(entries, key = { it.item.id }) { entry ->
                TvItemCard(entry.item, RowLayout.LANDSCAPE, { onOpenItem(entry.item) }, Modifier.width(TvLandscapeWidth), progress = entry.progress)
            }
        }
    }
}

@Composable
private fun TvSection(
    section: HomeSection,
    modifier: Modifier,
    onOpenItem: (CatalogItem) -> Unit,
    onOpenLink: (HomeLink) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val layout = section.row.layout
    Column {
        if (layout != RowLayout.HERO && section.title.isNotBlank()) {
            TvSectionTitle(section.title)
            Spacer(Modifier.height(12.dp))
        }
        when (val state = section.state) {
            SectionState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(TvRowPadding),
            ) {
                repeat(if (layout == RowLayout.HERO) 2 else 4) {
                    Box(
                        modifier = Modifier.width(if (layout == RowLayout.HERO) TvHeroWidth else layout.cardWidth())
                            .aspectRatio(layout.aspectRatio)
                            .clip(TvCardShape)
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
            is SectionState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(TvRowPadding),
            ) {
                Text(state.error.text(context), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            // Los enlaces ya están todos en la fila: sin "ver todo".
            is SectionState.Links -> LazyRow(
                contentPadding = TvRowPadding,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = modifier.focusRestorer(),
            ) {
                items(state.links, key = { it.url }) { link ->
                    TvImageTile(link.title, link.imageUrl, { onOpenLink(link) }, Modifier.width(TvLandscapeWidth))
                }
            }
            is SectionState.Loaded -> when (layout) {
                RowLayout.HERO -> TvHeroRow(state.items, onOpenItem, modifier)
                RowLayout.FEATURED -> state.items.firstOrNull()?.let { item ->
                    TvFeaturedCard(item, { onOpenItem(item) }, modifier.padding(TvRowPadding))
                }
                else -> LazyRow(
                    contentPadding = TvRowPadding,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = modifier.focusRestorer(),
                ) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                        TvItemCard(
                            item,
                            layout,
                            { onOpenItem(item) },
                            Modifier.width(layout.cardWidth()),
                            rank = (index + 1).takeIf { layout == RowLayout.RANKED },
                        )
                    }
                    item(key = "see-all") { TvSeeAllCard(layout, onSeeAll) }
                }
            }
        }
    }
}

/**
 * Destacado único (`ColeccionSuperDestacado`): imagen ancha y, al lado, título,
 * descripción y "Ver ahora". Una sola superficie enfocable.
 */
@Composable
private fun TvFeaturedCard(item: CatalogItem, onOpen: () -> Unit, modifier: Modifier) {
    TvFocusSurface(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(TvHeroWidth).aspectRatio(16f / 9f)) {
                TvArtwork(item.imageUrl, Modifier.fillMaxSize())
                item.live?.channelLogoUrl?.let { TvChannelLogo(it, Modifier.align(Alignment.TopStart)) }
            }
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.action_watch_now),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/**
 * Destacados como fila de tarjetas anchas con el título superpuesto, al estilo
 * de SmartTube: sin banner a pantalla completa, así se ven varias filas de golpe.
 */
@Composable
private fun TvHeroRow(items: List<CatalogItem>, onOpenItem: (CatalogItem) -> Unit, modifier: Modifier) {
    LazyRow(
        contentPadding = TvRowPadding,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.focusRestorer(),
    ) {
        items(items, key = { it.id }) { item ->
            TvFocusSurface(onClick = { onOpenItem(item) }, modifier = Modifier.width(TvHeroWidth).aspectRatio(16f / 9f)) {
                TvArtwork(item.imageUrl, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))),
                )
                item.live?.channelLogoUrl?.let { TvChannelLogo(it, Modifier.align(Alignment.TopStart)) }
                item.live?.takeIf { it.isOnAir }?.progressAt(LocalNowMillis.current)?.let { TvProgressStrip(it, Modifier.align(Alignment.BottomCenter)) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    if (item.live?.isOnAir == true) TvLiveDot(Modifier.padding(bottom = 4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private val TvHeroWidth = 380.dp
