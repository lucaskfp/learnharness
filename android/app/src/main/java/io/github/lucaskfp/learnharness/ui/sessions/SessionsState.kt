package io.github.lucaskfp.learnharness.ui.sessions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.lucaskfp.learnharness.R

enum class SessionKind(
    @StringRes val label: Int,
    @StringRes val newSessionLabel: Int,
    @DrawableRes val icon: Int,
) {
    Teaching(R.string.kind_teaching, R.string.new_teaching_session, R.drawable.ic_sessions),
    Practice(R.string.kind_practice, R.string.new_practice_session, R.drawable.ic_practice),
}

/** Where a Session stands: a Teaching phase, or the state of a Practice Session's problem. */
enum class SessionPhase(@StringRes val label: Int) {
    Probing(R.string.phase_probing),
    Planning(R.string.phase_planning),
    Teaching(R.string.phase_teaching),
    ProblemOpen(R.string.problem_open),
    ProblemConcluded(R.string.problem_concluded),
}

/** One row of the Sessions list. */
data class SessionSummary(
    val id: String,
    val goal: String,
    val kind: SessionKind,
    val phase: SessionPhase,
    val minutesSinceActivity: Long,
    val turnRunning: Boolean = false,
    val jobPending: Boolean = false,
)

enum class SessionFilter(@StringRes val label: Int) {
    All(R.string.filter_all),
    Teaching(R.string.filter_teaching),
    Practice(R.string.filter_practice),
}

/** Everything the Sessions screen shows. */
data class SessionsState(
    val sessions: List<SessionSummary> = emptyList(),
    val query: String = "",
    val filter: SessionFilter = SessionFilter.All,
    val fabMenuExpanded: Boolean = false,
    /** The Session awaiting delete confirmation, if any. */
    val pendingDelete: SessionSummary? = null,
) {
    /** The Sessions matching the filter and the query, most recent first. */
    val visibleSessions: List<SessionSummary>
        get() = sessions
            .filter {
                when (filter) {
                    SessionFilter.All -> true
                    SessionFilter.Teaching -> it.kind == SessionKind.Teaching
                    SessionFilter.Practice -> it.kind == SessionKind.Practice
                }
            }
            .filter { query.isBlank() || it.goal.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.minutesSinceActivity }
}
