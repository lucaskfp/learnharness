package io.github.lucaskfp.learnharness.ui.newsession

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lucaskfp.learnharness.app.AppContainer
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind

/** New Session wired to the Session engine; [onStarted] gets the id of the Session it started. */
@Composable
fun NewSessionRoute(container: AppContainer, kind: SessionKind, onClose: () -> Unit, onStarted: (String) -> Unit) {
    val viewModel: NewSessionViewModel = viewModel(
        key = "new-session-$kind",
        factory = viewModelFactory { initializer { NewSessionViewModel(kind, container.settings, container.engine) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    NewSessionScreen(
        state = state,
        onClose = onClose,
        onGoalChange = viewModel::onGoalChange,
        onModelSelect = viewModel::onModelSelect,
        onLanguageSelect = viewModel::onLanguageSelect,
        onKindMenuToggle = viewModel::onKindMenuToggle,
        onKindSelect = viewModel::onKindSelect,
        onStart = { viewModel.onStart(onStarted) },
    )
}
