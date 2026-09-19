package es.openrtve.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import es.openrtve.domain.BlockReason
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.ExploreCategory
import es.openrtve.domain.HomeLink
import es.openrtve.domain.HomeRow
import es.openrtve.domain.LinkKind
import es.openrtve.domain.collectionRow
import es.openrtve.domain.liveRow
import es.openrtve.domain.referenceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Tab { HOME, SEARCH, EXPLORE }

/** Cualquier pantalla que puede ocupar el host principal. */
sealed interface AppDestination

/** Pantallas apilables sobre la raíz de cada pestaña. */
sealed interface Destination : AppDestination {
    data class Portada(val url: String, val title: String) : Destination
    data class Module(val row: HomeRow, val title: String) : Destination
    data class Program(val item: CatalogItem) : Destination
    data class Video(val item: CatalogItem) : Destination
}

/** Pantallas globales: no pertenecen ni se guardan en la pila de una pestaña. */
sealed interface GlobalDestination : AppDestination {
    data object Settings : GlobalDestination
}

/** Una categoría abre su portada; un canal temático, la lista con su directo. */
fun ExploreCategory.destination(): Destination =
    if (isLive) Destination.Module(liveRow(contentUrl, title), title) else Destination.Portada(contentUrl, title)

/**
 * Destino de un enlace de portada. Programa, vídeo y audio necesitan el id de su
 * URL de API; si no se reconoce, el enlace no lleva a ningún sitio.
 */
fun HomeLink.destination(): Destination? = when (kind) {
    LinkKind.COLLECTION -> Destination.Module(collectionRow(url, title), title)
    LinkKind.PORTADA -> Destination.Portada(url, title)
    LinkKind.PROGRAM -> apiId?.let { Destination.Program(referenceItem(it, ContentKind.PROGRAM, title, imageUrl)) }
    LinkKind.VIDEO -> apiId?.let { Destination.Video(referenceItem(it, ContentKind.VIDEO, title, imageUrl)) }
    LinkKind.AUDIO -> apiId?.let { Destination.Video(referenceItem(it, ContentKind.AUDIO, title, imageUrl)) }
}

/** Mensajes efímeros que la UI muestra una vez y descarta. */
sealed interface UiMessage {
    data class Error(val error: LoadError) : UiMessage
    data class Blocked(val reason: BlockReason) : UiMessage
    data class Text(val text: String) : UiMessage
    /** Directo programado: "Empieza hoy · 16:10". */
    data class StartsAt(val schedule: String) : UiMessage
    data object LinkNotFound : UiMessage
}

/**
 * Clave estable de una entrada de navegación: la raíz de la pestaña o su posición
 * en la pila. Un mismo destino puede repetirse en la pila (programa -> vídeo ->
 * programa), así que la clave es la posición y no el destino.
 */
fun entryKey(tab: Tab, index: Int): String = if (index < 0) "$tab/root" else "$tab/$index"

fun globalEntryKey(destination: GlobalDestination): String = when (destination) {
    GlobalDestination.Settings -> "global/settings"
}

data class NavigationState(
    val tab: Tab = Tab.HOME,
    val stacks: Map<Tab, List<Destination>> = Tab.entries.associateWith { emptyList() },
    val global: GlobalDestination? = null,
    val message: UiMessage? = null,
) {
    val stack: List<Destination> get() = stacks.getValue(tab)
    val current: AppDestination? get() = global ?: stack.lastOrNull()
    val canGoBack: Boolean get() = global != null || stack.isNotEmpty()

    /** Clave de la pantalla visible. */
    val currentKey: String get() = global?.let(::globalEntryKey) ?: entryKey(tab, stack.lastIndex)

    /** Claves de todas las entradas vivas, en cualquier pestaña. */
    val liveKeys: Set<String>
        get() = stacks
            .flatMapTo(mutableSetOf()) { (tab, stack) -> (-1 until stack.size).map { entryKey(tab, it) } }
            .apply { global?.let { add(globalEntryKey(it)) } }
}

/**
 * Navegación con una pila por pestaña y destinos globales. La implementación
 * conserva las mismas invariantes que Navigation: inicio fijo, pilas
 * independientes, back predecible y deep links con pila sintética.
 *
 * Cada entrada tiene su propio [ViewModelStore], que se libera al salir de la
 * pila: así los ViewModels de las fichas no se acumulan durante toda la sesión.
 */
class NavigationViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = mutableState.asStateFlow()

    private val stores = mutableMapOf<String, EntryStoreOwner>()

    /** Dueño de los ViewModels de la entrada [key]; se crea la primera vez que se pide y es estable después. */
    fun storeOwner(key: String): ViewModelStoreOwner = stores.getOrPut(key, ::EntryStoreOwner)

    private class EntryStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    fun selectTab(tab: Tab) {
        val current = mutableState.value
        // Un destino global se cierra al elegir cualquier pestaña. Si se elige
        // la que había debajo, se recupera exactamente su posición y su pila.
        if (current.global != null) {
            mutableState.value = current.copy(tab = tab, global = null)
            release(globalEntryKey(current.global))
            return
        }
        if (current.tab != tab) {
            mutableState.value = current.copy(tab = tab)
            return
        }
        // Repetir la pestaña activa vuelve a su raíz, como en las apps M3.
        mutableState.value = current.copy(stacks = current.stacks + (tab to emptyList()))
        current.stack.indices.forEach { release(entryKey(tab, it)) }
    }

    fun push(destination: Destination) {
        val current = mutableState.value
        current.global?.let { release(globalEntryKey(it)) }
        mutableState.value = current.copy(
            stacks = current.stacks + (current.tab to current.stack + destination),
            global = null,
        )
    }

    fun openGlobal(destination: GlobalDestination) {
        val current = mutableState.value
        if (current.global == destination) return
        current.global?.let { release(globalEntryKey(it)) }
        mutableState.value = current.copy(global = destination)
    }

    /**
     * Un enlace externo crea una pila sintética alcanzable de forma normal:
     * Inicio -> destino. No hereda la pestaña ni la pila que estuvieran visibles.
     */
    fun openDeepLink(destination: Destination) {
        val current = mutableState.value
        current.global?.let { release(globalEntryKey(it)) }
        current.stacks.forEach { (tab, stack) ->
            stack.indices.forEach { release(entryKey(tab, it)) }
        }
        val emptyStacks = Tab.entries.associateWith { emptyList<Destination>() }
        mutableState.value = current.copy(
            tab = Tab.HOME,
            stacks = emptyStacks + (Tab.HOME to listOf(destination)),
            global = null,
        )
    }

    fun pop() {
        val current = mutableState.value
        if (current.global != null) {
            mutableState.value = current.copy(global = null)
            release(globalEntryKey(current.global))
            return
        }
        if (current.stack.isEmpty()) return
        mutableState.value = current.copy(stacks = current.stacks + (current.tab to current.stack.dropLast(1)))
        release(current.currentKey)
    }

    fun show(message: UiMessage) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    fun dismissMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private fun release(key: String) {
        stores.remove(key)?.viewModelStore?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.viewModelStore.clear() }
        stores.clear()
    }
}
