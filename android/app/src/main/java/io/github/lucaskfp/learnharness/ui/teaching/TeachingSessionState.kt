package io.github.lucaskfp.learnharness.ui.teaching

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase

/** The kinds of question an agent asks (upstream tools `probe`, `recall`, `quiz` and `explain`). */
enum class QuestionKind(@StringRes val label: Int, val freeRecall: Boolean, val graded: Boolean = true) {
    /** A Prober multiple-choice question. */
    Probe(R.string.question_probe, freeRecall = false),

    /** A Prober free-recall question, answered in the learner's own words. */
    Recall(R.string.question_recall, freeRecall = true),

    /** A Teacher question asked before explaining: the learner predicts first. */
    Prediction(R.string.question_prediction, freeRecall = false),

    /** A Teacher quiz that checks understanding after an explanation. */
    Check(R.string.question_check, freeRecall = false),

    /** An agent's ungraded question (`ask_user_question`): no right answer, so no confidence. */
    Ask(R.string.question_ask, freeRecall = false, graded = false),
}

/** How a quiz option is shown: picked or not, and after grading, right or wrong. */
enum class OptionState { Idle, Selected, Correct, Incorrect, MissedCorrect }

data class QuestionOption(val text: String, val state: OptionState = OptionState.Idle)

/** Confidence stops in percent, as on desktop (`CONFIDENCE_STOPS`); the slider moves between them. */
val ConfidenceStops = listOf(0, 30, 50, 70, 90, 100)

/** The slider starts at 50%, as on desktop (`DEFAULT_CONFIDENCE_INDEX`). */
val DefaultConfidenceIndex = ConfidenceStops.indexOf(50)

/** The question waiting for the learner, shown in the dock in place of the composer. */
data class PendingQuestion(
    val kind: QuestionKind,
    val text: String,
    val options: List<QuestionOption> = emptyList(),
    /** Index into [ConfidenceStops]. Unused by free recall, which asks for no confidence. */
    val confidenceIndex: Int = DefaultConfidenceIndex,
    /** The learner picked "I don't know" instead of an option. */
    val dontKnow: Boolean = false,
    val answer: String = "",
    val thinking: String = "",
    /** 1-based position among the questions waiting, and how many wait. */
    val position: Int = 1,
    val count: Int = 1,
    val collapsed: Boolean = false,
    /** Answered in words rather than by choosing an option. */
    val freeText: Boolean = kind.freeRecall,
)

data class PlanNode(val id: String, val title: String)

/** The Teacher's lesson map: a dependency graph with the current and visited nodes. */
data class Plan(
    val nodes: List<PlanNode>,
    val edges: List<PlanEdge>,
    val current: String? = null,
    val visited: Set<String> = emptySet(),
)

enum class JobKind(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Research(R.string.job_research, R.drawable.ic_research),
    Pdf(R.string.job_pdf, R.drawable.ic_pdf),
    Visual(R.string.job_visual, R.drawable.ic_visual),
    Demo(R.string.job_demo, R.drawable.ic_demo),
}

sealed interface JobStatus {
    /** Still computing; [progress] is 0..1. */
    data class Pending(val progress: Float, val activity: String) : JobStatus
    data object Done : JobStatus
    data class Failed(val reason: String) : JobStatus
}

/** A label pinned to a point of the learner's image, as fractions of its width and height. */
data class ImageAnnotation(val label: String, val x: Float, val y: Float)

/** One entry of a Session's transcript. */
sealed interface TranscriptItem {
    data class LearnerMessage(val text: String) : TranscriptItem

    /** Agent prose in Markdown: may hold ```math (LaTeX) and ```mermaid fences. */
    data class AgentProse(val markdown: String) : TranscriptItem

    /** The model's reasoning, when it exposes it. Collapsed by default. */
    data class Reasoning(val text: String, val expanded: Boolean = false) : TranscriptItem

    data class AnsweredQuestion(
        val kind: QuestionKind,
        val text: String,
        val options: List<QuestionOption> = emptyList(),
        val answer: String = "",
        /** Confidence in percent, one of [ConfidenceStops]. */
        val confidence: Int? = null,
        val thinking: String = "",
    ) : TranscriptItem

    /** Where the Session enters a new phase; the Planning seam carries the Plan's node count. */
    data class PhaseBoundary(val phase: SessionPhase, val nodeCount: Int? = null) : TranscriptItem

    /** A frozen snapshot of the Plan as the Teacher made it. */
    data class PlanSnapshot(val plan: Plan) : TranscriptItem

    /** The learner's own image, shown by the Teacher with labels pointing at parts of it. */
    data class ImageCard(val caption: String, val annotations: List<ImageAnnotation>) : TranscriptItem

    data class JobCard(val kind: JobKind, val title: String, val status: JobStatus) : TranscriptItem
}

/** Everything a Teaching Session screen shows. */
data class TeachingSessionState(
    val goal: String,
    val phase: SessionPhase,
    val transcript: List<TranscriptItem>,
    val plan: Plan? = null,
    val question: PendingQuestion? = null,
    val turnRunning: Boolean = false,
    val composerText: String = "",
)
