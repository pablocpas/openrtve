package es.openrtve.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import es.openrtve.ui.HomeSection
import es.openrtve.ui.PortadaViewModel
import es.openrtve.ui.SectionState
import es.openrtve.ui.text

/**
 * Portada al estilo Findroid: lista vertical de secciones, cada una con su propio
 * carrusel. La raíz no lleva barra superior: el hero llega hasta el borde y la
 * marca y los ajustes flotan sobre él.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortadaScreen(
    repository: CatalogRepository,
    url: String,
    title: String,
    onBack: (() -> Unit)?,
    onOpenItem: (CatalogItem) -> Unit,
    onOpenRow: (HomeRow, String) -> Unit,
    onError: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val viewModel: PortadaViewModel = viewModel(
        key = "portada-$url",
        factory = viewModelFactory { initializer { PortadaViewModel(repository, url) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRoot = onBack == null

    state.error?.let { error ->
        val text = error.text(context)
        LaunchedEffect(error) {
            onError(text)
            viewModel.dismissError()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!isRoot) {
            ScreenTopBar(title = title.ifBlank { state.title }, onBack = onBack)
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val startsWithHero = state.sections.firstOrNull()?.row?.layout == RowLayout.HERO
            when {
                state.isLoading -> LoadingPanel()
                state.sections.isEmpty() -> EmptyPanel(stringResource(R.string.state_empty_home), viewModel::refresh)
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isRoot && !startsWithHero) {
                        item(key = "top-inset") { Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 40.dp)) }
                    }
                    if (state.isStale) {
                        item(key = "stale") { StaleBanner() }
                    }
                    items(state.sections, key = { it.row.id }) { section ->
                        SectionView(
                            section = section,
                            fullBleedHero = isRoot && section.row.id == state.sections.first().row.id,
                            onOpenItem = onOpenItem,
                            onSeeAll = { onOpenRow(section.row, section.title) },
                            onRetry = { viewModel.retrySection(section.row) },
                        )
                    }
                }
            }
            if (isRoot) {
                FloatingBrandBar(onOpenSettings)
            }
        }
    }
}

/** Marca y ajustes sobre un degradado, en lugar de una barra de aplicación que solo ocupa. */
@Composable
private fun FloatingBrandBar(onOpenSettings: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent))
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPadding, end = 4.dp)
                .height(48.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (onOpenSettings != null) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun StaleBanner() {
    Text(
        text = stringResource(R.string.state_stale),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
    )
}

@Composable
private fun SectionView(
    section: HomeSection,
    fullBleedHero: Boolean,
    onOpenItem: (CatalogItem) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val layout = section.row.layout
    Column {
        // El hero no lleva cabecera: la imagen y el título ya son la cabecera.
        if (layout != RowLayout.HERO && section.title.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ScreenPadding, end = 4.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSeeAll) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_see_all))
                }
            }
        }
        when (val state = section.state) {
            SectionState.Loading -> PlaceholderRow(layout, fullBleedHero)
            is SectionState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            ) {
                Text(
                    text = state.error.text(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            is SectionState.Loaded -> when (layout) {
                RowLayout.HERO -> HeroPager(state.items, onOpenItem, fullBleed = fullBleedHero)
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        when (layout) {
                            RowLayout.POSTER -> PosterCard(item, { onOpenItem(item) }, Modifier.width(PosterWidth))
                            RowLayout.SQUARE -> SquareCard(item, { onOpenItem(item) }, Modifier.width(SquareWidth))
                            else -> ItemCard(item, { onOpenItem(item) }, Modifier.width(LandscapeWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderRow(layout: RowLayout, fullBleedHero: Boolean) {
    if (layout == RowLayout.HERO) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (fullBleedHero) 0.dp else ScreenPadding)
                .aspectRatio(if (fullBleedHero) 4f / 3f else 16f / 9f)
                .clip(if (fullBleedHero) androidx.compose.foundation.shape.RoundedCornerShape(0.dp) else CardShape)
                .background(MaterialTheme.colorScheme.surface),
        )
        return
    }
    val (width, ratio) = when (layout) {
        RowLayout.POSTER -> Pair(PosterWidth, 2f / 3f)
        RowLayout.SQUARE -> Pair(SquareWidth, 1f)
        else -> Pair(LandscapeWidth, 16f / 9f)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = ScreenPadding),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(width)
                    .aspectRatio(ratio)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

private val LandscapeWidth = 220.dp
private val PosterWidth = 130.dp
private val SquareWidth = 150.dp
