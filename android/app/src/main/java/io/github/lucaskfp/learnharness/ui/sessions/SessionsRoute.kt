package io.github.lucaskfp.learnharness.ui.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lucaskfp.learnharness.app.AppContainer

/** The Sessions list wired to the Session engine. */
@Composable
fun SessionsRoute(container: AppContainer, onOpen: (String) -> Unit, onNewSession: (SessionKind) -> Unit) {
    val viewModel: SessionsViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionsViewModel(container.engine, container.clock) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionsScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onOpen = { onOpen(it.id) },
        onRequestDelete = viewModel::onRequestDelete,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDismissDelete = viewModel::onDismissDelete,
        onFabMenuToggle = viewModel::onFabMenuToggle,
        onNewSession = { kind ->
            viewModel.onFabMenuToggle(false)
            onNewSession(kind)
        },
    )
}
