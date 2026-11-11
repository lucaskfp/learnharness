package io.github.lucaskfp.learnharness.ui.teaching

import io.github.lucaskfp.learnharness.engine.PendingQuestionView
import io.github.lucaskfp.learnharness.engine.SessionView
import io.github.lucaskfp.learnharness.engine.TranscriptEntry
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
    phase = SessionPhase.Probing,
    transcript = view.transcript.mapIndexed { index, entry -> entry.toItem(index in expandedReasoning) },
    question = view.pendingQuestion?.let { pending ->
        pending.toPending(draft.takeIf { it.toolCallId == pending.toolCallId } ?: QuestionDraft())
    },
    turnRunning = view.turnRunning,
    composerText = composerText,
)

private fun TranscriptEntry.toItem(reasoningExpanded: Boolean): TranscriptItem = when (this) {
    is TranscriptEntry.Learner -> TranscriptItem.LearnerMessage(text)
    is TranscriptEntry.AgentText -> TranscriptItem.AgentProse(markdown)
    is TranscriptEntry.Reasoning -> TranscriptItem.Reasoning(text, reasoningExpanded)
    is TranscriptEntry.Answered -> data.toItem()
}

private fun QuestionAnswerData.toItem(): TranscriptItem.AnsweredQuestion {
    val chosen = answers.map { it.index }.toSet()
    val correct = correctIndices.orEmpty().toSet()
    return TranscriptItem.AnsweredQuestion(
        kind = uiKind(tool),
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
    kind = uiKind(tool),
    text = text,
    options = options.map { QuestionOption(it.label, if (it.value in draft.selected) OptionState.Selected else OptionState.Idle) },
    confidenceIndex = draft.confidenceIndex,
    dontKnow = draft.dontKnow,
    answer = draft.answer,
    thinking = draft.thinking,
    collapsed = draft.collapsed,
    freeText = freeText,
)

/** Upstream's question tools, by the kind the dock and the answered cards label them with. */
private fun uiKind(tool: String?): QuestionKind = when (tool) {
    "probe" -> QuestionKind.Probe
    "recall" -> QuestionKind.Recall
    "ask_user_question" -> QuestionKind.Ask
    else -> QuestionKind.Check
}
