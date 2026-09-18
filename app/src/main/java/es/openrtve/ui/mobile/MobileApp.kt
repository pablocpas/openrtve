package es.openrtve.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
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
import es.openrtve.domain.RtveUrls
import es.openrtve.ui.Destination
import es.openrtve.ui.DestinationHost
import es.openrtve.ui.LocalNowMillis
import es.openrtve.ui.NavigationViewModel
import es.openrtve.ui.Tab
import es.openrtve.ui.rememberCatalogActions
import es.openrtve.ui.rememberNowMillis
import es.openrtve.ui.text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun MobileApp(
    container: AppContainer,
    /** Enlaces recibidos por la actividad (VIEW o Compartir). */
    incomingLinks: Flow<String> = emptyFlow(),
) {
    val repository = container.catalogRepository
    val navigation: NavigationViewModel = viewModel()
    val nav by navigation.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val actions = rememberCatalogActions(container, navigation)

    BackHandler(enabled = nav.canGoBack, onBack = navigation::pop)

    nav.message?.let { message ->
        val text = message.text(context)
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            navigation.dismissMessage()
        }
    }

    LaunchedEffect(incomingLinks, actions) {
        incomingLinks.collect(actions::openIncomingLink)
    }

    val nowMillis = rememberNowMillis()
    CompositionLocalProvider(LocalNowMillis provides nowMillis) {
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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                DestinationHost(nav, navigation) { destination ->
                    when (destination) {
                        is Destination.Portada -> PortadaScreen(
                            repository = repository,
                            url = destination.url,
                            title = destination.title,
                            onBack = navigation::pop,
                            onOpenItem = actions.openItem,
                            onOpenRow = actions.openRow,
                            onOpenLink = actions.openLink,
                            onError = actions.showMessage,
                        )
                        is Destination.Module -> ModuleScreen(
                            repository = repository,
                            row = destination.row,
                            title = destination.title,
                            onBack = navigation::pop,
                            onOpenItem = actions.openItem,
                            onError = actions.showMessage,
                        )
                        is Destination.Program -> ProgramScreen(
                            repository = repository,
                            history = container.watchHistory,
                            program = destination.item,
                            onBack = navigation::pop,
                            onOpenItem = actions.playItem,
                            onError = actions.showMessage,
                        )
                        Destination.Settings -> SettingsScreen(
                            container = container,
                            onBack = navigation::pop,
                            onMessage = actions.showMessage,
                        )
                        is Destination.Video -> VideoScreen(
                            repository = repository,
                            history = container.watchHistory,
                            item = destination.item,
                            onBack = navigation::pop,
                            onPlay = actions::play,
                            onOpenProgram = actions.openProgram,
                            onError = actions.showMessage,
                        )
                        null -> when (nav.tab) {
                            Tab.HOME -> PortadaScreen(
                                repository = repository,
                                url = RtveUrls.TV_HOME,
                                title = "",
                                history = container.watchHistory,
                                onBack = null,
                                onOpenItem = actions.openItem,
                                onOpenRow = actions.openRow,
                                onOpenLink = actions.openLink,
                                onError = actions.showMessage,
                                onOpenSettings = actions.openSettings,
                            )
                            Tab.SEARCH -> SearchScreen(
                                repository = repository,
                                onOpenItem = actions.openItem,
                                onError = actions.showMessage,
                            )
                            Tab.EXPLORE -> ExploreScreen(
                                repository = repository,
                                onOpenCategory = actions.openCategory,
                                onOpenSettings = actions.openSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}
