package es.openrtve.ui

import androidx.lifecycle.ViewModel
import es.openrtve.domain.BlockReason
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.HomeRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Tab { HOME, SEARCH, EXPLORE }

/** Pantallas apilables sobre la raíz de cada pestaña. */
sealed interface Destination {
    data class Portada(val url: String, val title: String) : Destination
    data class Module(val row: HomeRow, val title: String) : Destination
    data class Program(val item: CatalogItem) : Destination
    data class Video(val item: CatalogItem) : Destination
    data object Settings : Destination
}

/** Mensajes efímeros que la UI muestra una vez y descarta. */
sealed interface UiMessage {
    data class Error(val error: LoadError) : UiMessage
    data class Blocked(val reason: BlockReason) : UiMessage
    data class Text(val text: String) : UiMessage
}

data class NavigationState(
    val tab: Tab = Tab.HOME,
    val stacks: Map<Tab, List<Destination>> = Tab.entries.associateWith { emptyList() },
    val message: UiMessage? = null,
) {
    val stack: List<Destination> get() = stacks.getValue(tab)
    val current: Destination? get() = stack.lastOrNull()
    val canGoBack: Boolean get() = stack.isNotEmpty()
}

/**
 * Navegación con barra inferior y una pila por pestaña. Sin librería de
 * navegación: tres pestañas y tres tipos de destino no la justifican.
 */
class NavigationViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = mutableState.asStateFlow()

    fun selectTab(tab: Tab) {
        mutableState.update { current ->
            // Repetir la pestaña activa vuelve a su raíz, como en las apps M3.
            if (current.tab == tab) current.copy(stacks = current.stacks + (tab to emptyList())) else current.copy(tab = tab)
        }
    }

    fun push(destination: Destination) {
        mutableState.update { current ->
            current.copy(stacks = current.stacks + (current.tab to current.stack + destination))
        }
    }

    fun pop() {
        mutableState.update { current ->
            current.copy(stacks = current.stacks + (current.tab to current.stack.dropLast(1)))
        }
    }

    fun show(message: UiMessage) {
        mutableState.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }
}
