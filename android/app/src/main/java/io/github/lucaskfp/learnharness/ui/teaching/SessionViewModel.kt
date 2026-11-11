package io.github.lucaskfp.learnharness.ui.teaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lucaskfp.learnharness.engine.LearnerInput
import io.github.lucaskfp.learnharness.engine.PendingQuestionView
import io.github.lucaskfp.learnharness.engine.SessionEngine
import io.github.lucaskfp.learnharness.engine.SessionView
import io.github.lucaskfp.learnharness.engine.TurnInProgressException
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import io.github.lucaskfp.learnharness.engine.question.QuestionKind as EngineKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A Teaching Session screen's state: the engine's view plus what the learner is typing. */
class SessionViewModel(private val engine: SessionEngine, private val sessionId: String) : ViewModel() {
    private val draft = MutableStateFlow(QuestionDraft())
    private val composer = MutableStateFlow("")
    private val expandedReasoning = MutableStateFlow(emptySet<Int>())
    private val latest = MutableStateFlow<SessionView?>(null)

    /** The engine's view, kept in step with what is on screen. Null until loaded, or if unknown. */
    private val view: StateFlow<SessionView?> = engine.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<TeachingSessionState?> =
        combine(view, draft, composer, expandedReasoning) { view, draft, composer, expanded ->
            latest.value = view
            view?.let { toTeachingState(it, draft, composer, expanded) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Why the last Turn failed, if it did. */
    val error: StateFlow<String?> = combine(view) { (view) -> view?.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val pending: PendingQuestionView? get() = latest.value?.pendingQuestion

    /** Edits the draft of the question on screen, starting a fresh one when the question has changed. */
    private fun editDraft(change: QuestionDraft.() -> QuestionDraft) {
        val question = pending ?: return
        draft.update { current ->
            val base = if (current.toolCallId == question.toolCallId) current else QuestionDraft(toolCallId = question.toolCallId)
            base.change()
        }
    }

    fun onExpandQuestion() = editDraft { copy(collapsed = !collapsed) }

    fun onSelectOption(index: Int) {
        val question = pending ?: return
        val value = question.options.getOrNull(index)?.value ?: return
        editDraft {
            val chosen = when {
                question.multiSelect -> if (value in selected) selected - value else selected + value
                else -> setOf(value)
            }
            copy(selected = chosen, dontKnow = false)
        }
    }

    fun onDontKnow() = editDraft { copy(dontKnow = !dontKnow, selected = emptySet()) }

    fun onConfidenceChange(index: Int) = editDraft { copy(confidenceIndex = index) }

    fun onAnswerChange(text: String) = editDraft { copy(answer = text) }

    fun onThinkingChange(text: String) = editDraft { copy(thinking = text) }

    fun onComposerTextChange(text: String) {
        composer.value = text
    }

    fun onToggleReasoning(transcriptIndex: Int) =
        expandedReasoning.update { if (transcriptIndex in it) it - transcriptIndex else it + transcriptIndex }

    fun onSubmitAnswer() {
        val question = pending ?: return
        val current = draft.value.takeIf { it.toolCallId == question.toolCallId } ?: QuestionDraft(toolCallId = question.toolCallId)
        val request = answerRequest(question, current) ?: return
        draft.value = QuestionDraft()
        submit(LearnerInput.Answer(question.toolCallId, request))
    }

    fun onSend() {
        val text = composer.value.trim()
        if (text.isEmpty()) return
        composer.value = ""
        submit(LearnerInput.Message(text))
    }

    private fun submit(input: LearnerInput) {
        viewModelScope.launch {
            try {
                engine.submit(sessionId, input)
            } catch (_: TurnInProgressException) {
                // The screen holds input back while a Turn runs; a double tap lands here and is dropped.
            }
        }
    }

    private companion object {
        /** Null when there is nothing to submit yet. */
        fun answerRequest(question: PendingQuestionView, draft: QuestionDraft): AnswerRequest? {
            val thinking = draft.thinking.takeIf { it.isNotBlank() }
            return when {
                question.freeText -> draft.answer.takeIf { it.isNotBlank() }?.let { AnswerRequest(text = it, thinking = thinking) }
                question.kind == EngineKind.Quiz -> when {
                    draft.dontKnow -> AnswerRequest(dontKnow = true, thinking = thinking)
                    draft.selected.isNotEmpty() -> AnswerRequest(
                        values = draft.selected.toList(),
                        confidence = ConfidenceStops[draft.confidenceIndex],
                        thinking = thinking,
                    )
                    else -> null
                }
                else -> draft.selected.takeIf { it.isNotEmpty() }?.let { AnswerRequest(values = it.toList(), thinking = thinking) }
            }
        }
    }
}
