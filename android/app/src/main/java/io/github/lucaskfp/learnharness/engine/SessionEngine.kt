package io.github.lucaskfp.learnharness.engine

import io.github.lucaskfp.learnharness.engine.plan.PlanEdge
import io.github.lucaskfp.learnharness.engine.plan.PlanNode
import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import io.github.lucaskfp.learnharness.engine.question.QuestionAnswerData
import io.github.lucaskfp.learnharness.engine.question.QuestionKind
import io.github.lucaskfp.learnharness.engine.question.QuizOption
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class SessionKind { Teaching, Practice }

/** Where a Session stands. Teaching Sessions start in [Probing], move to [Planning] at a successful `probe_done`, and to [Teaching] at the first quiz tagged with a Plan node. */
enum class SessionPhase { Probing, Planning, Teaching }

/** Everything needed to start a Session. */
data class StartSession(
    val kind: SessionKind,
    val goal: String,
    val model: String,
    val teachingLanguage: TeachingLanguage,
)

/** What the learner can submit to a Session. */
sealed interface LearnerInput {
    /** The answer to the pending question, with confidence and thinking. */
    data class Answer(val toolCallId: String, val request: AnswerRequest) : LearnerInput

    /** A free message, when no question is pending. */
    data class Message(val text: String) : LearnerInput
}

/** One row of the Sessions list. */
data class SessionSummary(
    val id: String,
    val goal: String,
    val kind: SessionKind,
    val phase: SessionPhase,
    val updatedAt: Long,
    val turnRunning: Boolean,
)

/** The question waiting for the learner, options in display order. */
data class PendingQuestionView(
    val toolCallId: String,
    val tool: String,
    val kind: QuestionKind,
    val text: String,
    val details: String?,
    val options: List<QuizOption>,
    val multiSelect: Boolean,
    val freeText: Boolean,
    /** The Teacher quiz's role, `predict` or `check`; null for other questions. */
    val questionType: String? = null,
    /** 1-based place of this question among the questions its message posed together. */
    val position: Int = 1,
    /** How many questions its message posed together (answerable ones only). */
    val siblings: Int = 1,
)

/** The Plan as the learner sees it: the Teacher's lesson map and where the lesson stands on it. */
data class PlanView(
    val nodes: List<PlanNode>,
    val edges: List<PlanEdge>,
    /** The node the Teacher's latest tagged question is about; null before the first one. */
    val current: String?,
    /** Nodes of earlier stays. A revisited node is both visited and current. */
    val visited: Set<String>,
)

/** One entry of a Session's transcript, as the engine derives it from the saved history. */
sealed interface TranscriptEntry {
    data class Learner(val text: String) : TranscriptEntry

    /** Agent prose in Markdown. */
    data class AgentText(val markdown: String) : TranscriptEntry

    /** The model's reasoning, when it exposes it. */
    data class Reasoning(val text: String) : TranscriptEntry

    /** A question the learner has answered, graded where it is graded. */
    data class Answered(val data: QuestionAnswerData) : TranscriptEntry

    /**
     * Where the Session enters a new phase: Planning at the first Plan (with its node count) and
     * Teaching at the first question tagged with a Plan node.
     */
    data class PhaseBoundary(val phase: SessionPhase, val nodeCount: Int? = null) : TranscriptEntry

    /** The Plan as the Teacher made it, frozen. A replacement adds another snapshot. */
    data class PlanSnapshot(val plan: PlanView) : TranscriptEntry

    /** Where the learner stopped a Turn: what it generated before stays above. */
    data object Interrupted : TranscriptEntry
}

/**
 * The failed provider response the Session ends on. [retryable] when the learner can retry it
 * without new input: the failure follows the learner's input or completed tool results.
 */
data class FailureView(val message: String, val retryable: Boolean)

/** An unsent answer to a pending question, as the learner left it. */
@Serializable
data class AnswerDraft(
    val values: List<String> = emptyList(),
    val dontKnow: Boolean = false,
    /** The confidence in percent the learner set, if they moved it. */
    val confidence: Int? = null,
    val answer: String = "",
    val thinking: String = "",
)

/** Everything the learner typed in a Session and has not sent: the composer, and answers by question. */
data class SessionDrafts(
    val composer: String = "",
    /** Answer drafts by the question's tool call id. */
    val answers: Map<String, AnswerDraft> = emptyMap(),
)

/** A steering message waiting for the running Turn's next safe boundary. */
data class QueuedSteer(val id: String, val text: String)

/** A Session as the UI observes it. */
data class SessionView(
    val id: String,
    val goal: String,
    val kind: SessionKind,
    val phase: SessionPhase,
    /** Saved history, plus the output of a running Turn as it streams in. */
    val transcript: List<TranscriptEntry>,
    val pendingQuestion: PendingQuestionView?,
    /** The current Plan, once the Teacher has made one. */
    val plan: PlanView? = null,
    val turnRunning: Boolean,
    /** The failed response the Session ends on, until a retry or new input settles it. */
    val failure: FailureView?,
    /** Steering messages not yet delivered to the model, oldest first. */
    val queuedSteers: List<QueuedSteer> = emptyList(),
)

/** A second input arrived while a Turn was running: a Session runs one Turn at a time. */
class TurnInProgressException(sessionId: String) : IllegalStateException("A Turn is already running in $sessionId")

/** The Session does not end on a failed response that can be retried. */
class RetryNotAllowedException(sessionId: String) : IllegalStateException("Nothing to retry in $sessionId")

/** There is no question with that id waiting for an answer. */
class NoPendingQuestionException(toolCallId: String) : IllegalStateException("No pending question $toolCallId")

/**
 * The Session engine: the one module that owns Sessions end to end. The UI starts Sessions,
 * submits learner input and observes view state; Turns, agents, tools, providers and persistence
 * stay behind this interface.
 */
interface SessionEngine {
    /** Creates the Session, saves the learner's goal and begins its first Turn. */
    suspend fun start(request: StartSession): String

    /**
     * Saves the learner's input, then runs a Turn on it. An answer that leaves a sibling question
     * (posed in the same message) unanswered is only saved: the last answer runs the one Turn.
     * @throws TurnInProgressException if a Turn is already running in the Session.
     */
    suspend fun submit(sessionId: String, input: LearnerInput)

    /**
     * Stops the running Turn, keeping what it generated, and returns once it has settled and the
     * Session can take a new Turn. Does nothing when no Turn is running.
     */
    suspend fun abort(sessionId: String)

    /**
     * Steers the running Turn: the message is saved in a queue and delivered once, at the Turn's next
     * safe boundary (before its next model call; a Turn that would stop makes one more call). A Turn
     * that ends on a question, an abort or a failure leaves it queued for the Session's next Turn.
     * With no Turn running and no question pending, it is sent at once, like a message.
     */
    suspend fun steer(sessionId: String, text: String)

    /**
     * Retries the failed provider response the Session ends on, with no new input. The failed
     * attempts stay in the Session but are not sent to the model. Never happens on its own.
     * @throws RetryNotAllowedException if the Session does not end on a retryable failure.
     * @throws TurnInProgressException if a Turn is already running in the Session.
     */
    suspend fun retry(sessionId: String)

    /** What the learner left unsent in the Session, saved on the device so it survives the process. */
    suspend fun drafts(sessionId: String): SessionDrafts

    /** Saves the composer's unsent text; blank text forgets it. Sending a message or steer clears it. */
    suspend fun saveComposerDraft(sessionId: String, text: String)

    /** Saves an unsent answer; null or an empty draft forgets it. Answering the question clears it. */
    suspend fun saveAnswerDraft(sessionId: String, toolCallId: String, draft: AnswerDraft?)

    /** The Session's view state, updated as the Turn streams and settles. Null if unknown. */
    fun observe(sessionId: String): Flow<SessionView?>

    /** All Sessions, most recent first. */
    fun sessions(): Flow<List<SessionSummary>>

    suspend fun delete(sessionId: String)
}
