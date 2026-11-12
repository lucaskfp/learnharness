@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.truncatedByDesign
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase

/**
 * A Teaching Session, full-screen: the transcript, and at the bottom either the pending question
 * (dock) or the composer. On expanded widths, once there is a Plan, it stays beside the lesson.
 */
@Composable
fun TeachingSessionScreen(
    state: TeachingSessionState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    // Transcript
    onToggleReasoning: (TranscriptItem.Reasoning) -> Unit = {},
    onAbortJob: (TranscriptItem.JobCard) -> Unit = {},
    onOpenDemo: (TranscriptItem.JobCard) -> Unit = {},
    // Question dock
    onExpandQuestion: () -> Unit = {},
    onSelectOption: (Int) -> Unit = {},
    onDontKnow: () -> Unit = {},
    onConfidenceChange: (Int) -> Unit = {},
    onAnswerChange: (String) -> Unit = {},
    onThinkingChange: (String) -> Unit = {},
    onSubmitAnswer: () -> Unit = {},
    // Composer
    onComposerTextChange: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onFind: () -> Unit = {},
    onSend: () -> Unit = {},
    onStopTurn: () -> Unit = {},
    onRetry: () -> Unit = {},
    // Dictation, into the thinking field or the composer, whichever is showing
    onDictate: () -> Unit = {},
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val planBeside = wide && state.plan != null
    Scaffold(
        modifier = modifier,
        topBar = { SessionTopBar(state, showPlanAction = state.plan != null && !planBeside, onBack, onOpenPlan, onFind) },
        // The dock and the composer pad themselves above the navigation bar.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            // The lesson column owns the dock and composer, so a Plan beside it runs full height.
            Column(Modifier.weight(1f)) {
                Transcript(state, Modifier.weight(1f), onRetry) { item ->
                    TranscriptEntry(
                        item,
                        onOpenPlan = onOpenPlan,
                        onToggleReasoning = onToggleReasoning,
                        onAbortJob = onAbortJob,
                        onOpenDemo = onOpenDemo,
                    )
                }
                val question = state.question
                if (question != null) {
                    QuestionDock(
                        question,
                        deepThinking = state.phase == SessionPhase.Teaching,
                        onExpand = onExpandQuestion,
                        onSelect = onSelectOption,
                        onDontKnow = onDontKnow,
                        onConfidenceChange = onConfidenceChange,
                        onAnswerChange = onAnswerChange,
                        onThinkingChange = onThinkingChange,
                        onDictate = onDictate,
                        onSubmit = onSubmitAnswer,
                    )
                } else {
                    Composer(
                        state.composerText,
                        state.turnRunning,
                        onTextChange = onComposerTextChange,
                        onAttach = onAttach,
                        onDictate = onDictate,
                        onSend = onSend,
                        onStop = onStopTurn,
                    )
                }
            }
            if (planBeside) {
                VerticalDivider()
                PlanPane(state.plan!!, Modifier.width(PlanPaneWidth).fillMaxHeight())
            }
        }
    }
}

private val PlanPaneWidth = 400.dp

@Composable
private fun SessionTopBar(
    state: TeachingSessionState,
    showPlanAction: Boolean,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onFind: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
        title = {
            Column {
                Text(
                    state.goal,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.truncatedByDesign(),
                )
                Text(
                    stringResource(state.phase.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            // The running Turn stays visible however far the learner scrolls back.
            if (state.turnRunning) {
                val description = stringResource(R.string.turn_running)
                LoadingIndicator(Modifier.size(32.dp).semantics { contentDescription = description })
            }
            // Find sits here rather than in the composer's toolbar: there, at large font scales,
            // it squeezes the message field until its hint breaks mid-word.
            IconButton(onClick = onFind) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.find_in_session))
            }
            if (showPlanAction) {
                IconButton(onClick = onOpenPlan) {
                    Icon(painterResource(R.drawable.ic_plan), contentDescription = stringResource(R.string.open_plan))
                }
            }
        },
    )
}

@Composable
private fun Transcript(
    state: TeachingSessionState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    entry: @Composable (TranscriptItem) -> Unit,
) {
    val entries = state.transcript.size + (if (state.turnRunning) 1 else 0) + state.queuedSteers.size +
        (if (state.failure != null) 1 else 0)
    LazyColumn(
        // Opens at the latest entry, just above the dock or composer.
        state = rememberLazyListState(initialFirstVisibleItemIndex = (entries - 1).coerceAtLeast(0)),
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.transcript) { item -> entry(item) }
        if (state.turnRunning) {
            item { RunningTurnIndicator() }
        }
        items(state.queuedSteers) { text -> QueuedSteer(text) }
        state.failure?.let { failure -> item { FailureCard(failure, onRetry) } }
    }
}

/** The live Plan beside the lesson on wide windows. */
@Composable
private fun PlanPane(plan: Plan, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            pluralStringResource(R.plurals.plan_node_count, plan.nodes.size, plan.nodes.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ZoomablePlan(plan, Modifier.fillMaxWidth().weight(1f))
    }
}
