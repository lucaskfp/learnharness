package io.github.lucaskfp.learnharness.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lucaskfp.learnharness.engine.SessionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.lucaskfp.learnharness.engine.SessionKind as EngineKind
import io.github.lucaskfp.learnharness.engine.SessionPhase as EnginePhase
import io.github.lucaskfp.learnharness.engine.SessionSummary as EngineSummary

/** The Sessions list: the engine's Sessions, plus the search, filter, FAB and delete confirmation. */
class SessionsViewModel(
    private val engine: SessionEngine,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private data class Ui(
        val query: String = "",
        val filter: SessionFilter = SessionFilter.All,
        val fabMenuExpanded: Boolean = false,
        val pendingDeleteId: String? = null,
    )

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<SessionsState> = combine(engine.sessions(), ui) { sessions, ui ->
        val mapped = sessions.map { it.toUi(now()) }
        SessionsState(
            sessions = mapped,
            query = ui.query,
            filter = ui.filter,
            fabMenuExpanded = ui.fabMenuExpanded,
            pendingDelete = mapped.firstOrNull { it.id == ui.pendingDeleteId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsState())

    fun onQueryChange(query: String) = ui.update { it.copy(query = query) }

    fun onFilterChange(filter: SessionFilter) = ui.update { it.copy(filter = filter) }

    fun onFabMenuToggle(expanded: Boolean) = ui.update { it.copy(fabMenuExpanded = expanded) }

    fun onRequestDelete(session: SessionSummary) = ui.update { it.copy(pendingDeleteId = session.id) }

    fun onDismissDelete() = ui.update { it.copy(pendingDeleteId = null) }

    fun onConfirmDelete(session: SessionSummary) {
        ui.update { it.copy(pendingDeleteId = null) }
        viewModelScope.launch { engine.delete(session.id) }
    }

    private fun EngineSummary.toUi(now: Long) = SessionSummary(
        id = id,
        goal = goal,
        kind = when (kind) {
            EngineKind.Teaching -> SessionKind.Teaching
            EngineKind.Practice -> SessionKind.Practice
        },
        phase = when (phase) {
            EnginePhase.Probing -> SessionPhase.Probing
        },
        minutesSinceActivity = ((now - updatedAt) / 60_000).coerceAtLeast(0),
        turnRunning = turnRunning,
    )
}
