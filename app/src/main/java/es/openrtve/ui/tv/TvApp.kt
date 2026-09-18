package es.openrtve.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import es.openrtve.AppContainer
import es.openrtve.R
import es.openrtve.domain.BlockReason
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.referenceItem
import es.openrtve.domain.PlaybackDecision
import es.openrtve.domain.RtveUrls
import es.openrtve.playback.PlayerActivity
import es.openrtve.ui.Destination
import es.openrtve.ui.destination
import es.openrtve.ui.NavigationViewModel
import es.openrtve.ui.Tab
import es.openrtve.ui.UiMessage
import es.openrtve.ui.text
import es.openrtve.ui.scheduleLabel
import es.openrtve.ui.LocalNowMillis
import es.openrtve.ui.rememberNowMillis
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay

/**
 * Raíz de TV según las guías de Compose for TV: `NavigationDrawer` en el borde
 * izquierdo (rail de iconos que se expande al enfocar) y el contenido a la
 * derecha. Atrás lo maneja el mando; no hay botones de "Atrás" en pantalla.
 */
@Composable
fun TvApp(container: AppContainer) {
    val repository = container.catalogRepository
    val playbackResolver = container.playbackResolver
    val navigation: NavigationViewModel = viewModel()
    val nav by navigation.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = nav.canGoBack, onBack = navigation::pop)

    val message = nav.message?.text(context)
    if (message != null) {
        LaunchedEffect(message) {
            delay(MESSAGE_VISIBLE_MS)
            navigation.dismissMessage()
        }
    }

    val play: (CatalogItem, Boolean) -> Unit = { item, restart ->
        when (val decision = playbackResolver.resolve(item)) {
            is PlaybackDecision.Ready -> {
                container.watchHistory.register(item)
                context.startActivity(PlayerActivity.intent(context, decision, restart))
            }
            is PlaybackDecision.Blocked -> {
                val schedule = item.live?.scheduleLabel(context, System.currentTimeMillis())
                if (decision.reason == BlockReason.NOT_STARTED_YET && schedule != null) {
                    navigation.show(UiMessage.StartsAt(schedule))
                } else {
                    navigation.show(UiMessage.Blocked(decision.reason))
                }
            }
        }
    }
    val playItem: (CatalogItem) -> Unit = { play(it, false) }
    val openItem: (CatalogItem) -> Unit = { item ->
        when (item.kind) {
            ContentKind.PROGRAM -> navigation.push(Destination.Program(item))
            ContentKind.VIDEO, ContentKind.AUDIO -> navigation.push(Destination.Video(item))
            else -> playItem(item)
        }
    }
    val showError: (String) -> Unit = { navigation.show(UiMessage.Text(it)) }
    val destination = nav.current

    val nowMillis = rememberNowMillis()
    CompositionLocalProvider(LocalNowMillis provides nowMillis) {
    NavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Spacer(Modifier.height(TvVerticalMargin))
                DrawerEntry(Icons.Filled.Home, stringResource(R.string.tab_home), nav.tab == Tab.HOME && destination == null) { navigation.selectTab(Tab.HOME) }
                DrawerEntry(Icons.Filled.Search, stringResource(R.string.tab_search), nav.tab == Tab.SEARCH && destination == null) { navigation.selectTab(Tab.SEARCH) }
                DrawerEntry(Icons.Filled.Menu, stringResource(R.string.tab_explore), nav.tab == Tab.EXPLORE && destination == null) { navigation.selectTab(Tab.EXPLORE) }
                DrawerEntry(Icons.Filled.Settings, stringResource(R.string.settings_title), destination == Destination.Settings) {
                    if (destination != Destination.Settings) navigation.push(Destination.Settings)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                is Destination.Portada -> TvPortadaScreen(
                    repository = repository,
                    url = destination.url,
                    title = destination.title,
                    onOpenItem = openItem,
                    onOpenRow = { row, title -> navigation.push(Destination.Module(row, title)) },
                        onOpenLink = { link -> link.destination()?.let(navigation::push) },
                    onError = showError,
                )
                is Destination.Module -> TvModuleScreen(
                    repository = repository,
                    row = destination.row,
                    title = destination.title,
                    onOpenItem = openItem,
                    onError = showError,
                )
                is Destination.Program -> TvProgramScreen(
                    repository = repository,
                    history = container.watchHistory,
                    program = destination.item,
                    onOpenItem = playItem,
                    onError = showError,
                )
                is Destination.Video -> TvVideoScreen(
                    repository = repository,
                    history = container.watchHistory,
                    item = destination.item,
                    onPlay = play,
                    onOpenProgram = { id, title -> navigation.push(Destination.Program(referenceItem(id, ContentKind.PROGRAM, title))) },
                    onError = showError,
                )
                Destination.Settings -> TvSettingsScreen(container, onMessage = showError)
                null -> when (nav.tab) {
                    Tab.HOME -> TvPortadaScreen(
                        repository = repository,
                        url = RtveUrls.TV_HOME,
                        title = "",
                        history = container.watchHistory,
                        onOpenItem = openItem,
                        onOpenRow = { row, title -> navigation.push(Destination.Module(row, title)) },
                        onOpenLink = { link -> link.destination()?.let(navigation::push) },
                        onError = showError,
                    )
                    Tab.SEARCH -> TvSearchScreen(repository, openItem, showError)
                    Tab.EXPLORE -> TvExploreScreen(repository) {
                        navigation.push(it.destination())
                    }
                }
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TvVerticalMargin)
                        .background(MaterialTheme.colorScheme.primary, TvCardShape)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun NavigationDrawerScope.DrawerEntry(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null) },
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private const val MESSAGE_VISIBLE_MS = 4_000L
