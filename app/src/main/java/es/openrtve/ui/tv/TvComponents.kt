package es.openrtve.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import es.openrtve.R
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.RowLayout

/**
 * Pide el foco al entrar en la pantalla. Sin esto, al abrir un destino desde el
 * drawer el foco se queda en el drawer y la pantalla parece "muerta".
 */
@Composable
internal fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return focusRequester(requester)
}

/* Guía de diseño de TV: márgenes de overscan de 48 dp en horizontal y 27 dp en vertical. */
internal val TvHorizontalMargin = 48.dp
internal val TvVerticalMargin = 27.dp
internal val TvRowPadding = PaddingValues(horizontal = TvHorizontalMargin)
internal val TvCardShape = RoundedCornerShape(12.dp)
internal val TvLandscapeWidth = 260.dp
internal val TvPosterWidth = 150.dp
internal val TvSquareWidth = 180.dp

internal fun RowLayout.cardWidth(): Dp = when (this) {
    RowLayout.HERO, RowLayout.LANDSCAPE -> TvLandscapeWidth
    RowLayout.POSTER -> TvPosterWidth
    RowLayout.SQUARE -> TvSquareWidth
}

internal fun RowLayout.aspectRatio(): Float = when (this) {
    RowLayout.HERO, RowLayout.LANDSCAPE -> 16f / 9f
    RowLayout.POSTER -> 2f / 3f
    RowLayout.SQUARE -> 1f
}

/**
 * Superficie enfocable con la respuesta al foco que piden las guías de TV:
 * escala 1.1, borde claro y un ligero brillo. Es la base de todas las tarjetas.
 */
@Composable
internal fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = TvCardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), elevation = 12.dp),
        ),
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun TvArtwork(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        url?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun TvLiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.kind_live).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFD32F2F))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Tarjeta de contenido según la presentación de la fila. El texto va debajo, fuera de la zona que escala. */
@Composable
internal fun TvItemCard(
    item: CatalogItem,
    layout: RowLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = when (layout) {
        RowLayout.POSTER -> item.posterUrl ?: item.imageUrl
        RowLayout.SQUARE -> item.squareUrl ?: item.imageUrl ?: item.posterUrl
        else -> item.imageUrl
    }
    Column(modifier) {
        TvFocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(layout.aspectRatio())) {
            TvArtwork(image, Modifier.fillMaxSize())
            if (item.kind == ContentKind.LIVE) TvLiveBadge(Modifier.align(Alignment.TopStart))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = if (layout == RowLayout.POSTER) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (layout != RowLayout.POSTER) {
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Última tarjeta de cada fila: abre la rejilla completa sin sacar el foco de la fila. */
@Composable
internal fun TvSeeAllCard(layout: RowLayout, onClick: () -> Unit) {
    Column(Modifier.width(layout.cardWidth())) {
        TvFocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(layout.aspectRatio())) {
            Text(
                text = stringResource(R.string.action_see_all),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
internal fun TvImageTile(title: String, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvFocusSurface(onClick = onClick, modifier = modifier.aspectRatio(16f / 9f)) {
        TvArtwork(imageUrl, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        )
    }
}

@Composable
internal fun TvChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun TvScreenTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = TvHorizontalMargin, end = TvHorizontalMargin, top = TvVerticalMargin, bottom = 16.dp),
    )
}

@Composable
internal fun TvSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(TvRowPadding),
    )
}

@Composable
internal fun TvCenterMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.weight(1f))
        Text(message, style = MaterialTheme.typography.titleLarge)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        Spacer(Modifier.weight(1f))
    }
}
