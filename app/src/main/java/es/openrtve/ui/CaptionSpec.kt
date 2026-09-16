package es.openrtve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import es.openrtve.domain.CatalogItem

/** Líneas que reserva el texto de todas las tarjetas de una fila, calculadas con los items reales. */
data class CaptionSpec(
    val titleLines: Int,
    val subtitleLine: Boolean,
) {
    companion object {
        /** Sin medir: lo justo para un item suelto. */
        val Single = CaptionSpec(titleLines = 1, subtitleLine = true)
    }
}

/**
 * Mide los títulos de la fila al ancho real de la tarjeta y devuelve cuántas
 * líneas necesita el más largo (hasta [maxTitleLines]) y si alguno lleva
 * subtítulo. Así la fila tiene una altura constante (no salta al desplazarse)
 * pero solo la que hace falta.
 */
@Composable
fun rememberCaptionSpec(
    items: List<CatalogItem>,
    cardWidth: Dp,
    titleStyle: TextStyle,
    maxTitleLines: Int = 2,
    hasSubtitle: (CatalogItem) -> Boolean = { it.subtitle != null },
): CaptionSpec {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(items, cardWidth, titleStyle, maxTitleLines) {
        val constraints = Constraints(maxWidth = with(density) { cardWidth.roundToPx() }.coerceAtLeast(1))
        val lines = items.maxOfOrNull { item ->
            measurer.measure(
                text = item.title,
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxTitleLines,
                constraints = constraints,
            ).lineCount
        } ?: 1
        CaptionSpec(titleLines = lines.coerceIn(1, maxTitleLines), subtitleLine = items.any(hasSubtitle))
    }
}
