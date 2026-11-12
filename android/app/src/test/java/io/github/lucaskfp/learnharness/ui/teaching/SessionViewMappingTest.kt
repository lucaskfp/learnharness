package io.github.lucaskfp.learnharness.ui.teaching

import io.github.lucaskfp.learnharness.engine.FailureView
import io.github.lucaskfp.learnharness.engine.PendingQuestionView
import io.github.lucaskfp.learnharness.engine.QueuedSteer
import io.github.lucaskfp.learnharness.engine.PlanView
import io.github.lucaskfp.learnharness.engine.SessionKind
import io.github.lucaskfp.learnharness.engine.SessionView
import io.github.lucaskfp.learnharness.engine.TranscriptEntry
import io.github.lucaskfp.learnharness.engine.plan.PlanEdge as EnginePlanEdge
import io.github.lucaskfp.learnharness.engine.plan.PlanNode as EnginePlanNode
import io.github.lucaskfp.learnharness.engine.question.QuestionKind as EngineKind
import io.github.lucaskfp.learnharness.engine.question.QuizOption
import io.github.lucaskfp.learnharness.engine.SessionPhase as EnginePhase
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine's view of a Teaching Session as the screen draws it. */
class SessionViewMappingTest {
    private val plan = PlanView(
        nodes = listOf(EnginePlanNode("slope", "The slope idea"), EnginePlanNode("rule", "The power rule")),
        edges = listOf(EnginePlanEdge("slope", "rule")),
        current = "rule",
        visited = setOf("slope"),
    )

    private fun view(transcript: List<TranscriptEntry>, pending: PendingQuestionView? = null) = SessionView(
        id = "s",
        goal = "Derivatives",
        kind = SessionKind.Teaching,
        phase = EnginePhase.Teaching,
        transcript = transcript,
        pendingQuestion = pending,
        plan = plan,
        turnRunning = false,
        failure = null,
    )

    private fun state(view: SessionView, expanded: Set<Int> = emptySet()) =
        toTeachingState(view, QuestionDraft(), composerText = "", expandedReasoning = expanded)

    @Test
    fun reasoningIsCollapsedUntilTheLearnerOpensIt() {
        val transcript = listOf(TranscriptEntry.Reasoning("Slope first."), TranscriptEntry.AgentText("Here we go."))

        assertEquals(TranscriptItem.Reasoning("Slope first.", expanded = false), state(view(transcript)).transcript[0])
        assertEquals(TranscriptItem.Reasoning("Slope first.", expanded = true), state(view(transcript), expanded = setOf(0)).transcript[0])
    }

    @Test
    fun phaseThePlanAndItsSeamsReachTheScreen() {
        val transcript = listOf(
            TranscriptEntry.PhaseBoundary(EnginePhase.Planning, nodeCount = 2),
            TranscriptEntry.PlanSnapshot(plan.copy(current = null, visited = emptySet())),
            TranscriptEntry.PhaseBoundary(EnginePhase.Teaching),
        )

        val state = state(view(transcript))

        val expected = Plan(
            nodes = listOf(PlanNode("slope", "The slope idea"), PlanNode("rule", "The power rule")),
            edges = listOf(PlanEdge("slope", "rule")),
            current = "rule",
            visited = setOf("slope"),
        )
        assertEquals(SessionPhase.Teaching, state.phase)
        assertEquals(expected, state.plan)
        assertEquals(
            listOf(
                TranscriptItem.PhaseBoundary(SessionPhase.Planning, 2),
                TranscriptItem.PlanSnapshot(expected.copy(current = null, visited = emptySet())),
                TranscriptItem.PhaseBoundary(SessionPhase.Teaching),
            ),
            state.transcript,
        )
    }

    private fun quiz(questionType: String?) = PendingQuestionView(
        toolCallId = "q", tool = "quiz", kind = EngineKind.Quiz, text = "Guess?", details = null,
        options = listOf(QuizOption("A", "a")), multiSelect = false, freeText = false, questionType = questionType,
    )

    @Test
    fun aTeacherPredictionIsLabelledAsAPredictionAndOtherQuizzesAsChecks() {
        assertEquals(QuestionKind.Prediction, state(view(emptyList(), quiz("predict"))).question!!.kind)
        assertEquals(QuestionKind.Check, state(view(emptyList(), quiz("check"))).question!!.kind)
        assertEquals(QuestionKind.Check, state(view(emptyList(), quiz(null))).question!!.kind)
    }

    @Test
    fun turnControlStatesReachTheScreen() {
        val view = view(
            listOf(TranscriptEntry.AgentText("Exponents commute, so"), TranscriptEntry.Interrupted),
            pending = quiz("check").copy(position = 2, siblings = 3),
        ).copy(
            queuedSteers = listOf(QueuedSteer("steer_1", "Use small numbers")),
            failure = FailureView("Overloaded", retryable = true),
        )

        val state = state(view)

        assertEquals(listOf(TranscriptItem.AgentProse("Exponents commute, so"), TranscriptItem.Interrupted), state.transcript)
        assertEquals(listOf("Use small numbers"), state.queuedSteers)
        assertEquals(TurnFailure("Overloaded", retryable = true), state.failure)
        assertEquals(2 to 3, state.question!!.let { it.position to it.count })
    }
}
