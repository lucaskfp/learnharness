package io.github.lucaskfp.learnharness.ui.teaching

import io.github.lucaskfp.learnharness.engine.PendingQuestionView
import io.github.lucaskfp.learnharness.engine.PlanView
import io.github.lucaskfp.learnharness.engine.SessionPhase as EnginePhase
import io.github.lucaskfp.learnharness.engine.SessionView
import io.github.lucaskfp.learnharness.engine.TranscriptEntry
import io.github.lucaskfp.learnharness.engine.tools.ToolNames
import io.github.lucaskfp.learnharness.engine.question.QuestionAnswerData
import io.github.lucaskfp.learnharness.engine.question.QuestionKind as EngineKind
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase

/** What the learner has typed or picked for the pending question but not yet submitted. */
data class QuestionDraft(
    /** The question this draft belongs to; a draft for another question is ignored. */
    val toolCallId: String? = null,
    val selected: Set<String> = emptySet(),
    val dontKnow: Boolean = false,
    val confidenceIndex: Int = DefaultConfidenceIndex,
    val answer: String = "",
    val thinking: String = "",
    val collapsed: Boolean = false,
)

/** The engine's view of a Session, drawn by the Teaching Session screen. */
fun toTeachingState(
    view: SessionView,
    draft: QuestionDraft,
    composerText: String,
    expandedReasoning: Set<Int>,
): TeachingSessionState = TeachingSessionState(
    goal = view.goal,
    phase = view.phase.toUi(),
    plan = view.plan?.toUi(),
    transcript = view.transcript.mapIndexed { index, entry -> entry.toItem(index in expandedReasoning) },
    question = view.pendingQuestion?.let { pending ->
        pending.toPending(draft.takeIf { it.toolCallId == pending.toolCallId } ?: QuestionDraft())
    },
    turnRunning = view.turnRunning,
    composerText = composerText,
    queuedSteers = view.queuedSteers.map { it.text },
    failure = view.failure?.let { TurnFailure(it.message, it.retryable) },
)

private fun TranscriptEntry.toItem(reasoningExpanded: Boolean): TranscriptItem = when (this) {
    is TranscriptEntry.Learner -> TranscriptItem.LearnerMessage(text)
    is TranscriptEntry.AgentText -> TranscriptItem.AgentProse(markdown)
    is TranscriptEntry.Reasoning -> TranscriptItem.Reasoning(text, reasoningExpanded)
    is TranscriptEntry.Answered -> data.toItem()
    is TranscriptEntry.PhaseBoundary -> TranscriptItem.PhaseBoundary(phase.toUi(), nodeCount)
    is TranscriptEntry.PlanSnapshot -> TranscriptItem.PlanSnapshot(plan.toUi())
    TranscriptEntry.Interrupted -> TranscriptItem.Interrupted
}

private fun EnginePhase.toUi(): SessionPhase = when (this) {
    EnginePhase.Probing -> SessionPhase.Probing
    EnginePhase.Planning -> SessionPhase.Planning
    EnginePhase.Teaching -> SessionPhase.Teaching
}

private fun PlanView.toUi() = Plan(
    nodes = nodes.map { PlanNode(it.id, it.title) },
    edges = edges.map { PlanEdge(it.from, it.to) },
    current = current,
    visited = visited,
)

private fun QuestionAnswerData.toItem(): TranscriptItem.AnsweredQuestion {
    val chosen = answers.map { it.index }.toSet()
    val correct = correctIndices.orEmpty().toSet()
    return TranscriptItem.AnsweredQuestion(
        kind = uiKind(tool, questionType),
        text = questionText,
        options = options.orEmpty().map { option ->
            val state = when {
                kind == EngineKind.Quiz && option.index in chosen ->
                    if (option.index in correct) OptionState.Correct else OptionState.Incorrect
                kind == EngineKind.Quiz && option.index in correct -> OptionState.MissedCorrect
                option.index in chosen -> OptionState.Selected
                else -> OptionState.Idle
            }
            QuestionOption(option.label, state)
        },
        answer = listOfNotNull(text, other).joinToString("\n"),
        confidence = confidence,
        thinking = thinking.orEmpty(),
    )
}

private fun PendingQuestionView.toPending(draft: QuestionDraft) = PendingQuestion(
    kind = uiKind(tool, questionType),
    text = text,
    options = options.map { QuestionOption(it.label, if (it.value in draft.selected) OptionState.Selected else OptionState.Idle) },
    confidenceIndex = draft.confidenceIndex,
    dontKnow = draft.dontKnow,
    answer = draft.answer,
    thinking = draft.thinking,
    position = position,
    count = siblings,
    collapsed = draft.collapsed,
    freeText = freeText,
)

/** Upstream's question tools, by the kind the dock and the answered cards label them with. */
private fun uiKind(tool: String?, questionType: String?): QuestionKind = when (tool) {
    ToolNames.Probe -> QuestionKind.Probe
    ToolNames.Recall -> QuestionKind.Recall
    ToolNames.Ask -> QuestionKind.Ask
    else -> if (questionType == "predict") QuestionKind.Prediction else QuestionKind.Check
}
