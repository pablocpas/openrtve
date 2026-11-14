package es.openrtve.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import es.openrtve.R
import es.openrtve.data.Favorites
import es.openrtve.domain.CatalogItem

/** Una opción de un menú contextual de TV. */
internal class TvOption(val label: String, val onClick: () -> Unit)

/**
 * Menú contextual de TV, como el de Google TV: el título y una columna de botones.
 * Se abre con una pulsación larga de OK, así que el botón aún está pulsado al
 * aparecer: ver [ignoreHeldSelectKey]. Atrás lo cierra a la primera (Compose usa
 * Atrás antes para sacar el foco de su grupo).
 */
@Composable
internal fun TvOptionsDialog(title: String, options: List<TvOption>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // El diálogo es otra "pantalla": su primer botón recibe el foco al abrirse.
        ProvideScreenInitialFocus {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .ignoreHeldSelectKey()
                    .onPreviewKeyEvent { event ->
                        if (event.key != Key.Back && event.key != Key.Escape) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        true
                    }
                    .background(MaterialTheme.colorScheme.surface, TvCardShape)
                    .padding(24.dp)
                    .width(360.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                options.forEachIndexed { index, option ->
                    Button(
                        onClick = option.onClick,
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.initialFocus() else Modifier),
                    ) { Text(option.label) }
                }
            }
        }
    }
}

/**
 * Menú de pulsación larga de cualquier tarjeta de programa o vídeo: añadir o quitar
 * de "Mi lista" y ver la ficha (útil en un capítulo, que al pulsarlo se reproduce).
 * Hay uno para toda la app ([ProvideTvItemMenu]); las tarjetas lo abren con
 * [itemMenuLongClick].
 */
@Stable
internal class TvItemMenu(val favorites: Favorites) {
    var item by mutableStateOf<CatalogItem?>(null)
        private set

    fun show(item: CatalogItem) {
        this.item = item
    }

    fun dismiss() {
        item = null
    }
}

internal val LocalTvItemMenu = staticCompositionLocalOf<TvItemMenu?> { null }

/** La pulsación larga de una tarjeta; `null` si no hay menú (un directo, o fuera de la app). */
@Composable
internal fun itemMenuLongClick(item: CatalogItem): (() -> Unit)? {
    val menu = LocalTvItemMenu.current ?: return null
    if (!menu.favorites.canAdd(item)) return null
    return { menu.show(item) }
}

@Composable
internal fun ProvideTvItemMenu(
    favorites: Favorites,
    onOpenDetails: (CatalogItem) -> Unit,
    /** Quitar avisa (y en móvil deja deshacer); añadir se ve en el propio menú la próxima vez. */
    onRemove: (CatalogItem) -> Unit,
    content: @Composable () -> Unit,
) {
    val menu = remember(favorites) { TvItemMenu(favorites) }
    val openDetails by rememberUpdatedState(onOpenDetails)
    val remove by rememberUpdatedState(onRemove)
    CompositionLocalProvider(LocalTvItemMenu provides menu) {
        content()
        menu.item?.let { item ->
            val inList = favorites.isListed(item)
            TvOptionsDialog(
                title = item.title,
                options = listOf(
                    if (inList) {
                        TvOption(stringResource(R.string.favorites_remove)) { menu.dismiss(); remove(item) }
                    } else {
                        TvOption(stringResource(R.string.favorites_add)) { menu.dismiss(); favorites.toggle(item) }
                    },
                    TvOption(stringResource(R.string.continue_open_details)) { menu.dismiss(); openDetails(item) },
                ),
                onDismiss = menu::dismiss,
            )
        }
    }
}

/** Si [item] está en "Mi lista", observándola para que el menú cambie al momento. */
@Composable
internal fun Favorites.isListed(item: CatalogItem): Boolean {
    val listed by items.collectAsStateWithLifecycle()
    return remember(listed, item.id) { listed.any { it.id == item.id } }
}
