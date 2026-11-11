package io.github.lucaskfp.learnharness.engine

import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import io.github.lucaskfp.learnharness.engine.question.QuestionAnswerData
import io.github.lucaskfp.learnharness.engine.question.QuestionKind
import io.github.lucaskfp.learnharness.engine.question.QuizOption
import kotlinx.coroutines.flow.Flow

enum class SessionKind { Teaching, Practice }

/** Where a Session stands. Teaching Sessions start in [Probing]; later phases arrive with their issues. */
enum class SessionPhase { Probing }

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
}

/** A Session as the UI observes it. */
data class SessionView(
    val id: String,
    val goal: String,
    val kind: SessionKind,
    val phase: SessionPhase,
    /** Saved history, plus the output of a running Turn as it streams in. */
    val transcript: List<TranscriptEntry>,
    val pendingQuestion: PendingQuestionView?,
    val turnRunning: Boolean,
    /** Why the last Turn failed, until the next input. */
    val error: String?,
)

/** A second input arrived while a Turn was running: a Session runs one Turn at a time. */
class TurnInProgressException(sessionId: String) : IllegalStateException("A Turn is already running in $sessionId")

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
     * Saves the learner's input, then runs a Turn on it.
     * @throws TurnInProgressException if a Turn is already running in the Session.
     */
    suspend fun submit(sessionId: String, input: LearnerInput)

    /** The Session's view state, updated as the Turn streams and settles. Null if unknown. */
    fun observe(sessionId: String): Flow<SessionView?>

    /** All Sessions, most recent first. */
    fun sessions(): Flow<List<SessionSummary>>

    suspend fun delete(sessionId: String)
}
