package io.github.lucaskfp.learnharness.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase
import io.github.lucaskfp.learnharness.ui.teaching.PracticeProblem
import io.github.lucaskfp.learnharness.ui.teaching.ProblemOutcome
import io.github.lucaskfp.learnharness.ui.teaching.TeachingSessionState
import io.github.lucaskfp.learnharness.ui.teaching.TranscriptItem

/**
 * Practice Session states for the mockups (6 · Prática): a staircase problem on dynamic
 * programming, from the active problem through a hint to its conclusion. Text is in string
 * resources so each locale's mockup is in that language.
 */
object FakePractice {
    @Composable
    private fun opening(): List<TranscriptItem> = listOf(
        TranscriptItem.LearnerMessage(stringResource(R.string.fake_goal_dp)),
        TranscriptItem.AgentProse(stringResource(R.string.fake_p_intro)),
        TranscriptItem.ProblemCard(stringResource(R.string.fake_p_statement)),
    )

    @Composable
    private fun afterHint(): List<TranscriptItem> = opening() + listOf(
        TranscriptItem.ThinkingCard(stringResource(R.string.fake_p_thinking_1)),
        TranscriptItem.AgentProse(stringResource(R.string.fake_p_hint)),
    )

    /** A: the problem just posed, the learner thinking in the dock. */
    @Composable
    fun activeProblem() = TeachingSessionState(
        goal = stringResource(R.string.fake_goal_dp),
        phase = SessionPhase.ProblemOpen,
        transcript = opening(),
        problem = PracticeProblem(stringResource(R.string.fake_p_statement), thinking = stringResource(R.string.fake_p_thinking_1)),
    )

    /** B: a hint after the first submission, the dock collapsed by the learner. */
    @Composable
    fun hint() = TeachingSessionState(
        goal = stringResource(R.string.fake_goal_dp),
        phase = SessionPhase.ProblemOpen,
        transcript = afterHint(),
        problem = PracticeProblem(stringResource(R.string.fake_p_statement), collapsed = true),
    )

    /** C: the problem concluded and its solution revealed; the learner may ask, or go on. */
    @Composable
    fun concluded() = TeachingSessionState(
        goal = stringResource(R.string.fake_goal_dp),
        phase = SessionPhase.ProblemConcluded,
        transcript = afterHint() + listOf(
            TranscriptItem.ThinkingCard(stringResource(R.string.fake_p_thinking_2)),
            TranscriptItem.ConcludedCard(
                ProblemOutcome.SolvedWithHints,
                stringResource(R.string.fake_p_verdict),
                stringResource(R.string.fake_p_solution),
            ),
        ),
        nextAllowed = true,
        askAboutSolution = true,
    )

    /** D: the outcomes the Practitioner can record (a dismissal is not possible on Android yet). */
    val outcomes = listOf(ProblemOutcome.Solved, ProblemOutcome.SolvedWithHints, ProblemOutcome.Surrendered, ProblemOutcome.MovedOn)
}
