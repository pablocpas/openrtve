package es.openrtve.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.openrtve.R
import es.openrtve.data.Favorites
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.RowLayout

/**
 * "Mi lista" en TV: lo guardado, lo último primero, en rejilla. Al pulsar se abre
 * la ficha; manteniendo OK, el menú de la tarjeta permite quitarlo.
 *
 * Al quitar la tarjeta que tenía el foco, este pasa a la que ocupa ahora su sitio
 * (o a la anterior, si era la última): sin esto se quedaba en el aire y el mando
 * no movía nada hasta la siguiente pulsación.
 */
@Composable
fun TvMyListScreen(favorites: Favorites, onOpenItem: (CatalogItem) -> Unit) {
    val items by favorites.items.collectAsStateWithLifecycle()
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var targetId by remember { mutableStateOf<String?>(null) }
    val refocus = remember { FocusRequester() }

    LaunchedEffect(items) {
        val lost = focusedId ?: return@LaunchedEffect
        if (items.isEmpty() || items.any { it.id == lost }) return@LaunchedEffect
        val target = items[focusedIndex.coerceAtMost(items.lastIndex)].id
        targetId = target
        requestFocusUntilGranted(refocus) { focusedId == target }
        targetId = null
    }

    Column(Modifier.fillMaxSize()) {
        TvScreenTitle(stringResource(R.string.favorites_title))
        if (items.isEmpty()) {
            TvCenterMessage(stringResource(R.string.favorites_empty_tv))
        } else {
            TvItemGrid(items, RowLayout.LANDSCAPE, onOpenItem) { index, item ->
                Modifier
                    .onFocusChanged {
                        if (it.hasFocus) {
                            focusedId = item.id
                            focusedIndex = index
                        }
                    }
                    .then(if (item.id == targetId) Modifier.focusRequester(refocus) else Modifier)
            }
        }
    }
}
