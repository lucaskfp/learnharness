package io.github.lucaskfp.learnharness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.lucaskfp.learnharness.app.AppContainer
import io.github.lucaskfp.learnharness.ui.destinations.DestinationScreen
import io.github.lucaskfp.learnharness.ui.navigation.TopLevelDestination
import io.github.lucaskfp.learnharness.ui.newsession.NewSessionRoute
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind
import io.github.lucaskfp.learnharness.ui.sessions.SessionsRoute
import io.github.lucaskfp.learnharness.ui.setup.SetupScreen
import io.github.lucaskfp.learnharness.ui.setup.SetupViewModel
import io.github.lucaskfp.learnharness.ui.teaching.SessionRoute
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme
import kotlinx.serialization.Serializable

/** The destinations above the navigation shell: they take the whole screen. */
@Serializable
internal data object MainKey : NavKey

@Serializable
internal data class NewSessionKey(val practice: Boolean) : NavKey

@Serializable
internal data class SessionKey(val id: String) : NavKey

/**
 * The app: first-run Setup until a key is saved, then the navigation shell with the Sessions list,
 * New Session and the Session itself, all driven by the Session engine.
 */
@Composable
fun AppRoot(container: AppContainer) {
    LearnHarnessTheme {
        val configured by remember(container) { container.settings.observeConfigured() }.collectAsStateWithLifecycle(null)
        when (configured) {
            // Reading the saved settings takes a moment: show the surface, not Setup.
            null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            false -> SetupRoute(container)
            true -> MainRoutes(container)
        }
    }
}

@Composable
private fun SetupRoute(container: AppContainer) {
    val viewModel: SetupViewModel = viewModel(
        factory = viewModelFactory { initializer { SetupViewModel(container.settings, container.keyTester) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        onProviderSelect = viewModel::onProviderSelect,
        onApiKeyChange = viewModel::onApiKeyChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelNameChange = viewModel::onModelNameChange,
        onTestKey = viewModel::onTestKey,
        onModelSelect = viewModel::onModelSelect,
        onContinue = viewModel::onContinue,
    )
}

@Composable
private fun MainRoutes(container: AppContainer) {
    val backStack = rememberNavBackStack(MainKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<MainKey> {
                NavigationShell { destination ->
                    if (destination == TopLevelDestination.Sessions) {
                        SessionsRoute(
                            container,
                            onOpen = { backStack.add(SessionKey(it)) },
                            onNewSession = { backStack.add(NewSessionKey(practice = it == SessionKind.Practice)) },
                        )
                    } else {
                        DestinationScreen(destination)
                    }
                }
            }
            entry<NewSessionKey> { key ->
                NewSessionRoute(
                    container,
                    kind = if (key.practice) SessionKind.Practice else SessionKind.Teaching,
                    onClose = { backStack.removeLastOrNull() },
                    onStarted = { id ->
                        // The Session replaces the form: Back from it returns to the list.
                        backStack.removeLastOrNull()
                        backStack.add(SessionKey(id))
                    },
                )
            }
            entry<SessionKey> { key -> SessionRoute(container, key.id, onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
