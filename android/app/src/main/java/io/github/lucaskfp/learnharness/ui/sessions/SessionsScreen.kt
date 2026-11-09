@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)

package io.github.lucaskfp.learnharness.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.LhMark
import io.github.lucaskfp.learnharness.ui.components.truncatedByDesign
import kotlinx.coroutines.launch

/**
 * The Sessions list: search, kind filter, one row per Session and a FAB menu to start a new one.
 * On wide windows it is the list pane of a list–detail layout.
 */
@Composable
fun SessionsScreen(
    state: SessionsState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onFilterChange: (SessionFilter) -> Unit = {},
    onOpen: (SessionSummary) -> Unit = {},
    onRequestDelete: (SessionSummary) -> Unit = {},
    onConfirmDelete: (SessionSummary) -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onFabMenuToggle: (Boolean) -> Unit = {},
    onNewSession: (SessionKind) -> Unit = {},
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    ListDetailPaneScaffold(
        directive = directive,
        value = calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination = ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.List),
        ),
        modifier = modifier,
        listPane = {
            AnimatedPane {
                SessionsList(
                    state = state,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onOpen = onOpen,
                    onRequestDelete = onRequestDelete,
                    onFabMenuToggle = onFabMenuToggle,
                    onNewSession = onNewSession,
                )
            }
        },
        detailPane = { AnimatedPane { NoSessionSelected() } },
    )
    state.pendingDelete?.let { session ->
        DeleteSessionDialog(session, onConfirm = { onConfirmDelete(session) }, onDismiss = onDismissDelete)
    }
}

@Composable
private fun SessionsList(
    state: SessionsState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SessionFilter) -> Unit,
    onOpen: (SessionSummary) -> Unit,
    onRequestDelete: (SessionSummary) -> Unit,
    onFabMenuToggle: (Boolean) -> Unit,
    onNewSession: (SessionKind) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            SessionsSearchBar(state.query, onQueryChange)
            FilterChips(state.filter, onFilterChange)
            val shown = state.visibleSessions
            when {
                state.sessions.isEmpty() -> EmptyState(
                    title = stringResource(R.string.empty_sessions),
                    body = stringResource(R.string.empty_sessions_hint),
                )
                shown.isEmpty() -> EmptyState(
                    title = stringResource(R.string.sessions_no_match, state.query),
                    body = null,
                )
                else -> LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = FabClearance),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { session ->
                        SwipeToDeleteRow(session, onRequestDelete) {
                            SessionRow(session = session, onClick = { onOpen(session) })
                        }
                    }
                }
            }
        }
        NewSessionFabMenu(
            expanded = state.fabMenuExpanded,
            onToggle = onFabMenuToggle,
            onNewSession = onNewSession,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

private val FabClearance = 96.dp

@Composable
private fun SessionsSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(query)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(onQueryChange)
    }
    SearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {},
                placeholder = { Text(stringResource(R.string.sessions_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilterChips(selected: SessionFilter, onSelect: (SessionFilter) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SwipeToDeleteRow(
    session: SessionSummary,
    onRequestDelete: (SessionSummary) -> Unit,
    content: @Composable () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestDelete(session)
            // The row stays until the learner confirms the delete.
            scope.launch { swipeState.reset() }
        },
        backgroundContent = {
            // Drawn only mid-swipe: at rest it would show around the row's rounded corners.
            if (swipeState.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}

/** Kind icon, goal, phase and last activity, and whether a Turn is running or a Job pending. */
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Icon(painterResource(session.kind.icon), contentDescription = stringResource(session.kind.label))
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.session_meta,
                    stringResource(session.phase.label),
                    lastActivity(session.minutesSinceActivity),
                ),
            )
        },
        trailingContent = { ActivityIndicators(session) },
    ) {
        Text(
            text = session.goal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.truncatedByDesign(),
        )
    }
}

@Composable
private fun ActivityIndicators(session: SessionSummary) {
    if (!session.turnRunning && !session.jobPending) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (session.turnRunning) {
            val description = stringResource(R.string.turn_running)
            LoadingIndicator(
                Modifier
                    .size(IndicatorSize)
                    .semantics { contentDescription = description },
            )
        }
        if (session.jobPending) {
            Icon(
                painterResource(R.drawable.ic_job_pending),
                contentDescription = stringResource(R.string.job_pending),
                modifier = Modifier.size(IndicatorSize),
            )
        }
    }
}

private val IndicatorSize = 24.dp

@Composable
private fun lastActivity(minutes: Long): String = when {
    minutes < 1 -> stringResource(R.string.activity_just_now)
    minutes < 60 -> stringResource(R.string.activity_minutes, minutes)
    minutes < 60 * 24 -> stringResource(R.string.activity_hours, minutes / 60)
    else -> stringResource(R.string.activity_days, minutes / (60 * 24))
}

@Composable
private fun EmptyState(title: String, body: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NewSessionFabMenu(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onNewSession: (SessionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = onToggle) {
                Icon(
                    painterResource(if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add),
                    contentDescription = stringResource(if (expanded) R.string.close_menu else R.string.new_session_menu),
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        SessionKind.entries.forEach { kind ->
            FloatingActionButtonMenuItem(
                onClick = { onNewSession(kind) },
                icon = { Icon(painterResource(kind.icon), contentDescription = null) },
                text = { Text(stringResource(kind.newSessionLabel)) },
            )
        }
    }
}

/** The detail pane before a Session is opened. */
@Composable
private fun NoSessionSelected() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        LhMark()
        Text(
            text = stringResource(R.string.select_session),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteSessionDialog(session: SessionSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.delete_session_title)) },
        text = { Text(stringResource(R.string.delete_session_body, session.goal)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
