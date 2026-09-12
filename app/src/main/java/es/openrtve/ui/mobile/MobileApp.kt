package es.openrtve.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.openrtve.AppContainer
import es.openrtve.R
import es.openrtve.domain.CatalogItem
import es.openrtve.domain.ContentKind
import es.openrtve.domain.PlaybackDecision
import es.openrtve.domain.RtveUrls
import es.openrtve.playback.PlayerActivity
import es.openrtve.ui.Destination
import es.openrtve.ui.NavigationViewModel
import es.openrtve.ui.Tab
import es.openrtve.ui.UiMessage
import es.openrtve.ui.text

@Composable
fun MobileApp(container: AppContainer) {
    val repository = container.catalogRepository
    val playbackResolver = container.playbackResolver
    val navigation: NavigationViewModel = viewModel()
    val nav by navigation.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = nav.canGoBack, onBack = navigation::pop)

    nav.message?.let { message ->
        val text = message.text(context)
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            navigation.dismissMessage()
        }
    }

    val playItem: (CatalogItem) -> Unit = { item ->
        when (val decision = playbackResolver.resolve(item)) {
            is PlaybackDecision.Ready -> context.startActivity(PlayerActivity.intent(context, decision))
            is PlaybackDecision.Blocked -> navigation.show(UiMessage.Blocked(decision.reason))
        }
    }
    // Programas y vídeos abren su ficha; directos y audios se reproducen al momento.
    val openItem: (CatalogItem) -> Unit = { item ->
        when (item.kind) {
            ContentKind.PROGRAM -> navigation.push(Destination.Program(item))
            ContentKind.VIDEO -> navigation.push(Destination.Video(item))
            else -> playItem(item)
        }
    }
    val showError: (String) -> Unit = { navigation.show(UiMessage.Text(it)) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = nav.tab == Tab.HOME,
                    onClick = { navigation.selectTab(Tab.HOME) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = nav.tab == Tab.SEARCH,
                    onClick = { navigation.selectTab(Tab.SEARCH) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_search)) },
                )
                NavigationBarItem(
                    selected = nav.tab == Tab.EXPLORE,
                    onClick = { navigation.selectTab(Tab.EXPLORE) },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_explore)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        androidx.compose.foundation.layout.Box(modifier) {
            when (val destination = nav.current) {
                is Destination.Portada -> PortadaScreen(
                    repository = repository,
                    url = destination.url,
                    title = destination.title,
                    onBack = navigation::pop,
                    onOpenItem = openItem,
                    onOpenRow = { row, title -> navigation.push(Destination.Module(row, title)) },
                    onError = showError,
                )
                is Destination.Module -> ModuleScreen(
                    repository = repository,
                    row = destination.row,
                    title = destination.title,
                    onBack = navigation::pop,
                    onOpenItem = openItem,
                    onError = showError,
                )
                is Destination.Program -> ProgramScreen(
                    repository = repository,
                    program = destination.item,
                    onBack = navigation::pop,
                    onOpenItem = playItem,
                    onError = showError,
                )
                Destination.Settings -> SettingsScreen(
                    container = container,
                    onBack = navigation::pop,
                    onMessage = showError,
                )
                is Destination.Video -> VideoScreen(
                    repository = repository,
                    item = destination.item,
                    onBack = navigation::pop,
                    onPlay = playItem,
                    onOpenProgram = { id, title -> navigation.push(Destination.Program(programStub(id, title))) },
                    onError = showError,
                )
                null -> when (nav.tab) {
                    Tab.HOME -> PortadaScreen(
                        repository = repository,
                        url = RtveUrls.TV_HOME,
                        title = "",
                        onBack = null,
                        onOpenItem = openItem,
                        onOpenRow = { row, title -> navigation.push(Destination.Module(row, title)) },
                        onError = showError,
                        onOpenSettings = { navigation.push(Destination.Settings) },
                    )
                    Tab.SEARCH -> SearchScreen(
                        repository = repository,
                        onOpenItem = openItem,
                        onError = showError,
                    )
                    Tab.EXPLORE -> ExploreScreen(
                        repository = repository,
                        onOpenCategory = { navigation.push(Destination.Portada(it.portadaUrl, it.title)) },
                        onOpenSettings = { navigation.push(Destination.Settings) },
                    )
                }
            }
        }
    }
}

/** Ficha mínima para abrir un programa del que solo conocemos id y nombre. */
private fun programStub(id: String, title: String) = CatalogItem(
    id = id,
    playbackId = null,
    assetId = null,
    title = title,
    subtitle = null,
    imageUrl = null,
    kind = ContentKind.PROGRAM,
    directQualityUrl = null,
    allowedInCountry = null,
    loginRequired = false,
    paid = false,
    drm = false,
    programId = id,
)
