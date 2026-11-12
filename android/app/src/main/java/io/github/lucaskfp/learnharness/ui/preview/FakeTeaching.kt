package io.github.lucaskfp.learnharness.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase
import io.github.lucaskfp.learnharness.ui.teaching.ImageAnnotation
import io.github.lucaskfp.learnharness.ui.teaching.JobKind
import io.github.lucaskfp.learnharness.ui.teaching.JobStatus
import io.github.lucaskfp.learnharness.ui.teaching.OptionState
import io.github.lucaskfp.learnharness.ui.teaching.PendingQuestion
import io.github.lucaskfp.learnharness.ui.teaching.Plan
import io.github.lucaskfp.learnharness.ui.teaching.PlanEdge
import io.github.lucaskfp.learnharness.ui.teaching.PlanNode
import io.github.lucaskfp.learnharness.ui.teaching.QuestionKind
import io.github.lucaskfp.learnharness.ui.teaching.QuestionOption
import io.github.lucaskfp.learnharness.ui.teaching.TeachingSessionState
import io.github.lucaskfp.learnharness.ui.teaching.TranscriptItem
import io.github.lucaskfp.learnharness.ui.teaching.TurnFailure

/**
 * Teaching Session states for the mockups: a Session on public-key cryptography, through Probe,
 * Plan and Teach. Text is in string resources so each locale's mockup is in that language; the
 * LaTeX and Mermaid sources are language-neutral.
 */
object FakeTeaching {
    private const val EXCHANGE_LATEX = """(g^a)^b \equiv (g^b)^a \equiv g^{ab} \pmod{p}"""
    private const val EXCHANGE_MERMAID = "sequenceDiagram\nAlice->>Bob: A = g^a mod p\nBob->>Alice: B = g^b mod p"

    @Composable
    private fun goal() = stringResource(R.string.fake_goal_crypto)

    // Probe

    @Composable
    private fun probeTranscript(): List<TranscriptItem> = listOf(
        TranscriptItem.LearnerMessage(goal()),
        TranscriptItem.AgentProse(stringResource(R.string.fake_t_probe_intro)),
        TranscriptItem.AnsweredQuestion(
            kind = QuestionKind.Probe,
            text = stringResource(R.string.fake_t_mod_question),
            options = listOf(
                QuestionOption("2", OptionState.Correct),
                QuestionOption("3"),
                QuestionOption("12"),
            ),
            confidence = 90,
            thinking = stringResource(R.string.fake_t_mod_thinking),
        ),
    )

    @Composable
    private fun publicKeyQuestion() = PendingQuestion(
        kind = QuestionKind.Probe,
        text = stringResource(R.string.fake_t_public_question),
        options = listOf(
            QuestionOption(stringResource(R.string.fake_t_public_a)),
            QuestionOption(stringResource(R.string.fake_t_public_b), OptionState.Selected),
            QuestionOption(stringResource(R.string.fake_t_public_c)),
        ),
        confidenceIndex = 3,
        thinking = stringResource(R.string.fake_t_public_thinking),
        position = 1,
        count = 3,
    )

    @Composable
    fun probeMultipleChoice() = TeachingSessionState(
        goal = goal(),
        phase = SessionPhase.Probing,
        transcript = probeTranscript(),
        question = publicKeyQuestion(),
    )

    @Composable
    fun probeRecall() = probeMultipleChoice().copy(
        question = PendingQuestion(
            kind = QuestionKind.Recall,
            text = stringResource(R.string.fake_t_recall_question),
            answer = stringResource(R.string.fake_t_recall_answer),
            position = 2,
            count = 3,
        ),
    )

    @Composable
    fun probeCollapsed() = probeMultipleChoice().copy(question = publicKeyQuestion().copy(collapsed = true))

    /** The Prober is writing the next questions; the learner can steer meanwhile. */
    @Composable
    fun probeRunning() = probeMultipleChoice().copy(question = null, turnRunning = true)

    // Plan

    @Composable
    fun plan(current: String? = null, visited: Set<String> = emptySet()) = Plan(
        nodes = listOf(
            PlanNode("modular", stringResource(R.string.fake_t_node_modular)),
            PlanNode("oneway", stringResource(R.string.fake_t_node_oneway)),
            PlanNode("dlog", stringResource(R.string.fake_t_node_dlog)),
            PlanNode("trapdoor", stringResource(R.string.fake_t_node_trapdoor)),
            PlanNode("dh", stringResource(R.string.fake_t_node_dh)),
            PlanNode("rsa", stringResource(R.string.fake_t_node_rsa)),
            PlanNode("goal", stringResource(R.string.fake_t_node_goal)),
        ),
        edges = listOf(
            PlanEdge("modular", "dlog"),
            PlanEdge("oneway", "dlog"),
            PlanEdge("oneway", "trapdoor"),
            PlanEdge("dlog", "dh"),
            PlanEdge("modular", "rsa"),
            PlanEdge("trapdoor", "rsa"),
            PlanEdge("dh", "goal"),
            PlanEdge("rsa", "goal"),
        ),
        current = current,
        visited = visited,
    )

    @Composable
    fun teachingPlan() = plan(current = "dh", visited = setOf("modular", "oneway", "dlog"))

    @Composable
    fun planInTranscript(): TeachingSessionState {
        val plan = plan()
        return TeachingSessionState(
            goal = goal(),
            phase = SessionPhase.Planning,
            transcript = probeTranscript() + listOf(
                TranscriptItem.PhaseBoundary(SessionPhase.Planning, nodeCount = plan.nodes.size),
                TranscriptItem.AgentProse(stringResource(R.string.fake_t_plan_intro)),
                TranscriptItem.PlanSnapshot(plan),
                TranscriptItem.AgentProse(stringResource(R.string.fake_t_plan_reply)),
            ),
            plan = plan,
        )
    }

    // Teach

    @Composable
    private fun predictionAnswered() = TranscriptItem.AnsweredQuestion(
        kind = QuestionKind.Prediction,
        text = stringResource(R.string.fake_t_prediction_question),
        options = listOf(
            QuestionOption(stringResource(R.string.fake_t_prediction_yes), OptionState.Correct),
            QuestionOption(stringResource(R.string.fake_t_prediction_no)),
        ),
        confidence = 50,
        thinking = stringResource(R.string.fake_t_prediction_thinking),
    )

    @Composable
    private fun explanation() = TranscriptItem.AgentProse(
        stringResource(R.string.fake_t_dh_lead) +
            "\n\n```math\n$EXCHANGE_LATEX\n```\n\n" +
            stringResource(R.string.fake_t_dh_steps) +
            "\n\n```mermaid\n$EXCHANGE_MERMAID\n```\n\n" +
            stringResource(R.string.fake_t_dh_eve),
    )

    @Composable
    private fun teach(transcript: List<TranscriptItem>, question: PendingQuestion? = null, turnRunning: Boolean = false) =
        TeachingSessionState(
            goal = goal(),
            phase = SessionPhase.Teaching,
            transcript = listOf(TranscriptItem.PhaseBoundary(SessionPhase.Teaching)) + transcript,
            plan = teachingPlan(),
            question = question,
            turnRunning = turnRunning,
        )

    /** The Teacher asks for a prediction before explaining. */
    @Composable
    fun teachPrediction() = teach(
        transcript = listOf(TranscriptItem.AgentProse(stringResource(R.string.fake_t_plan_reply))),
        question = PendingQuestion(
            kind = QuestionKind.Prediction,
            text = stringResource(R.string.fake_t_prediction_question),
            options = listOf(
                QuestionOption(stringResource(R.string.fake_t_prediction_yes), OptionState.Selected),
                QuestionOption(stringResource(R.string.fake_t_prediction_no)),
            ),
            thinking = stringResource(R.string.fake_t_prediction_thinking),
        ),
    )

    /** Markdown, LaTeX and Mermaid, with the model's reasoning expanded above it. */
    @Composable
    fun teachExplanation() = teach(
        transcript = listOf(
            predictionAnswered(),
            TranscriptItem.Reasoning(stringResource(R.string.fake_t_reasoning), expanded = true),
            explanation(),
        ),
    )

    /**
     * Two graded checks: one answered correctly, one answered incorrectly with the correct option
     * marked as missed. The selected state shows in the Probe and Prediction docks.
     */
    @Composable
    fun teachQuizGraded() = teach(
        transcript = listOf(
            TranscriptItem.AnsweredQuestion(
                kind = QuestionKind.Check,
                text = stringResource(R.string.fake_t_check2_question),
                options = listOf(
                    QuestionOption(stringResource(R.string.fake_t_check2_a)),
                    QuestionOption(stringResource(R.string.fake_t_check2_b), OptionState.Correct),
                ),
                confidence = 70,
            ),
            TranscriptItem.AnsweredQuestion(
                kind = QuestionKind.Check,
                text = stringResource(R.string.fake_t_check_question),
                options = listOf(
                    QuestionOption(stringResource(R.string.fake_t_check_a), OptionState.Incorrect),
                    QuestionOption(stringResource(R.string.fake_t_check_b), OptionState.MissedCorrect),
                    QuestionOption(stringResource(R.string.fake_t_check_c)),
                ),
                confidence = 30,
                thinking = stringResource(R.string.fake_t_check_thinking),
            ),
        ),
    )

    /** The learner's image marked up by the Teacher, and a visual a Job generated. */
    @Composable
    fun teachImages() = teach(
        transcript = listOf(
            TranscriptItem.JobCard(JobKind.Visual, stringResource(R.string.fake_t_visual_title), JobStatus.Done),
            TranscriptItem.ImageCard(
                caption = stringResource(R.string.fake_t_photo_caption),
                annotations = listOf(
                    ImageAnnotation(stringResource(R.string.fake_t_photo_channel), x = 0.5f, y = 0.41f),
                    ImageAnnotation(stringResource(R.string.fake_t_photo_secret), x = 0.5f, y = 0.72f),
                ),
            ),
        ),
    )

    /** A finished demo and a research Job still computing, with wavy progress. */
    @Composable
    fun teachJobs() = teach(
        transcript = listOf(
            explanation(),
            TranscriptItem.JobCard(JobKind.Demo, stringResource(R.string.fake_t_demo_title), JobStatus.Done),
            TranscriptItem.JobCard(
                JobKind.Research,
                stringResource(R.string.fake_t_research_title),
                JobStatus.Pending(progress = 0.45f, activity = stringResource(R.string.fake_t_research_activity)),
            ),
        ),
    )

    /** A Turn is running: the loading indicator, a steering message being typed and Stop. */
    @Composable
    fun teachRunning() = teach(
        transcript = listOf(predictionAnswered(), explanation()),
        turnRunning = true,
    ).copy(composerText = stringResource(R.string.fake_t_steer))

    /** A steer waits for the running Turn's next safe point while the learner types another. */
    @Composable
    fun teachSteerQueued() = teach(
        transcript = listOf(predictionAnswered(), explanation()),
        turnRunning = true,
    ).copy(
        queuedSteers = listOf(stringResource(R.string.fake_t_steer)),
        composerText = stringResource(R.string.fake_t_steer_queued),
    )

    /** The model did not answer: the failure card offers "Try again". */
    @Composable
    fun teachFailed() = teach(
        transcript = listOf(predictionAnswered(), explanation(), TranscriptItem.LearnerMessage(stringResource(R.string.fake_t_steer))),
    ).copy(failure = TurnFailure(stringResource(R.string.fake_t_failure_reason), retryable = true))

    /** The learner stopped the Turn: its partial output stays, followed by the cut. */
    @Composable
    fun teachInterrupted() = teach(
        transcript = listOf(
            predictionAnswered(),
            explanation(),
            TranscriptItem.LearnerMessage(stringResource(R.string.fake_t_steer)),
            TranscriptItem.AgentProse(stringResource(R.string.fake_t_partial)),
            TranscriptItem.Interrupted,
        ),
    )
}
