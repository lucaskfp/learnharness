package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.app.AppContainer

/** A Teaching Session wired to the Session engine: the transcript, the pending question and the composer. */
@Composable
fun SessionRoute(container: AppContainer, sessionId: String, onBack: () -> Unit) {
    val viewModel: SessionViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory { initializer { SessionViewModel(container.engine, sessionId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failure = error?.let { stringResource(R.string.turn_failed, it) }
    LaunchedEffect(failure) { failure?.let { snackbar.showSnackbar(it) } }

    state?.let { session ->
        Box(Modifier.fillMaxSize()) {
            TeachingSessionScreen(
                state = session,
                onBack = onBack,
                onToggleReasoning = { item -> viewModel.onToggleReasoning(session.transcript.indexOf(item)) },
                onExpandQuestion = viewModel::onExpandQuestion,
                onSelectOption = viewModel::onSelectOption,
                onDontKnow = viewModel::onDontKnow,
                onConfidenceChange = viewModel::onConfidenceChange,
                onAnswerChange = viewModel::onAnswerChange,
                onThinkingChange = viewModel::onThinkingChange,
                onSubmitAnswer = viewModel::onSubmitAnswer,
                onComposerTextChange = viewModel::onComposerTextChange,
                onSend = viewModel::onSend,
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
