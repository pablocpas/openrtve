package es.openrtve.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Aloja la pantalla visible con el estado propio de su entrada de navegación:
 * un `SaveableStateHolder` conserva scroll y foco al volver atrás, y el
 * `ViewModelStore` de la entrada (de [NavigationViewModel]) aísla sus ViewModels.
 * El estado guardado de las entradas que salen de la pila se descarta.
 */
@Composable
fun DestinationHost(
    nav: NavigationState,
    navigation: NavigationViewModel,
    content: @Composable (AppDestination?) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val shown = remember { mutableSetOf<String>() }
    val key = nav.currentKey
    val liveKeys = nav.liveKeys
    LaunchedEffect(liveKeys) {
        (shown - liveKeys).forEach(holder::removeState)
        shown.retainAll(liveKeys)
    }
    SideEffect { shown += key }
    holder.SaveableStateProvider(key) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides navigation.storeOwner(key)) {
            content(nav.current)
        }
    }
}
