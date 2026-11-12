package io.github.lucaskfp.learnharness.ui.teaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lucaskfp.learnharness.engine.ActiveProblemView
import io.github.lucaskfp.learnharness.engine.AnswerDraft
import io.github.lucaskfp.learnharness.engine.NextNotAllowedException
import io.github.lucaskfp.learnharness.engine.NoActiveProblemException
import io.github.lucaskfp.learnharness.engine.LearnerInput
import io.github.lucaskfp.learnharness.engine.PendingQuestionView
import io.github.lucaskfp.learnharness.engine.RetryNotAllowedException
import io.github.lucaskfp.learnharness.engine.SessionEngine
import io.github.lucaskfp.learnharness.engine.SessionView
import io.github.lucaskfp.learnharness.engine.TurnInProgressException
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import io.github.lucaskfp.learnharness.engine.question.QuestionKind as EngineKind
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A Teaching Session screen's state: the engine's view plus what the learner is typing. Unsent
 * answers and the composer's text are saved through the engine as drafts, so they survive leaving
 * the screen and the process being killed.
 */
class SessionViewModel(private val engine: SessionEngine, private val sessionId: String) : ViewModel() {
    private val draft = MutableStateFlow(QuestionDraft())

    /** The learner's thinking on the active practice problem; keyed, like answers, by the call's id. */
    private val problemDraft = MutableStateFlow(QuestionDraft())
    private val composer = MutableStateFlow("")
    private val expandedReasoning = MutableStateFlow(emptySet<Int>())
    private val latest = MutableStateFlow<SessionView?>(null)

    /** Answer drafts saved on the device, by question, as they were when the screen opened. */
    private val restored = MutableStateFlow<Map<String, AnswerDraft>>(emptyMap())

    /** The engine's view, kept in step with what is on screen. Null until loaded, or if unknown. */
    private val view: StateFlow<SessionView?> = engine.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<TeachingSessionState?> =
        combine(view, combine(draft, problemDraft, ::Pair), composer, expandedReasoning, restored) { view, drafts, composer, expanded, _ ->
            latest.value = view
            view?.let {
                val (draft, problem) = drafts
                toTeachingState(it, draftFor(it.pendingQuestion, draft), composer, expanded, problemDraftFor(it.activeProblem, problem))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val saved = engine.drafts(sessionId)
            restored.value = saved.answers
            // Typing that beat the read wins over what was saved.
            composer.update { it.ifEmpty { saved.composer } }
            persistDrafts()
        }
    }

    /** Saves the drafts a moment after the learner stops typing, starting from what is on screen now. */
    @OptIn(FlowPreview::class)
    private fun persistDrafts() {
        viewModelScope.launch {
            composer.debounce(DraftDelayMs).distinctUntilChanged().collect { engine.saveComposerDraft(sessionId, it) }
        }
        viewModelScope.launch {
            problemDraft.debounce(DraftDelayMs).distinctUntilChanged().collect { current ->
                val toolCallId = current.toolCallId ?: return@collect
                engine.saveAnswerDraft(sessionId, toolCallId, AnswerDraft(thinking = current.thinking))
            }
        }
        viewModelScope.launch {
            draft.debounce(DraftDelayMs).distinctUntilChanged().collect { current ->
                val toolCallId = current.toolCallId ?: return@collect
                engine.saveAnswerDraft(sessionId, toolCallId, current.toAnswerDraft())
            }
        }
    }

    private val pending: PendingQuestionView? get() = latest.value?.pendingQuestion

    /** The draft of [question]: the one being edited, else the one saved for it, else a fresh one. */
    private fun draftFor(question: PendingQuestionView?, current: QuestionDraft): QuestionDraft = when {
        question == null -> QuestionDraft()
        current.toolCallId == question.toolCallId -> current
        else -> restored.value[question.toolCallId]?.toQuestionDraft(question.toolCallId) ?: QuestionDraft(toolCallId = question.toolCallId)
    }

    /** The draft of [problem]: the one being edited, else the one saved for it, else a fresh one. */
    private fun problemDraftFor(problem: ActiveProblemView?, current: QuestionDraft): QuestionDraft = when {
        problem == null -> QuestionDraft()
        current.toolCallId == problem.toolCallId -> current
        else -> QuestionDraft(toolCallId = problem.toolCallId, thinking = restored.value[problem.toolCallId]?.thinking.orEmpty())
    }

    private fun editProblemDraft(change: QuestionDraft.() -> QuestionDraft) {
        val problem = latest.value?.activeProblem ?: return
        problemDraft.update { current -> problemDraftFor(problem, current).change() }
    }

    fun onToggleProblem() = editProblemDraft { copy(collapsed = !collapsed) }

    fun onProblemThinkingChange(text: String) = editProblemDraft { copy(thinking = text) }

    /** Submits the thinking on the active problem; the dock stays, the problem is still active. */
    fun onSubmitThinking() {
        val problem = latest.value?.activeProblem ?: return
        val current = problemDraftFor(problem, problemDraft.value)
        val text = current.thinking.trim()
        if (text.isEmpty()) return
        problemDraft.value = current.copy(thinking = "")
        restored.update { it - problem.toolCallId }
        submit(LearnerInput.Thinking(problem.toolCallId, text))
    }

    /** The learner's go-ahead for the next problem. */
    fun onNextProblem() {
        if (latest.value?.nextAllowed != true) return
        submit(LearnerInput.Next)
    }

    /** Edits the draft of the question on screen. */
    private fun editDraft(change: QuestionDraft.() -> QuestionDraft) {
        val question = pending ?: return
        draft.update { current -> draftFor(question, current).change() }
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
        val current = draftFor(question, draft.value)
        val request = answerRequest(question, current) ?: return
        draft.value = QuestionDraft()
        restored.update { it - question.toolCallId }
        submit(LearnerInput.Answer(question.toolCallId, request))
    }

    /** Sends the composer's text: it steers a running Turn, or starts one. */
    fun onSend() {
        val text = composer.value.trim()
        if (text.isEmpty()) return
        composer.value = ""
        if (latest.value?.turnRunning == true) {
            viewModelScope.launch { engine.steer(sessionId, text) }
        } else {
            submit(LearnerInput.Message(text))
        }
    }

    fun onStopTurn() {
        viewModelScope.launch { engine.abort(sessionId) }
    }

    fun onRetry() {
        viewModelScope.launch {
            try {
                engine.retry(sessionId)
            } catch (_: TurnInProgressException) {
                // A double tap: the first retry is already running.
            } catch (_: RetryNotAllowedException) {
                // The failure was settled meanwhile; the card goes away with it.
            }
        }
    }

    private fun submit(input: LearnerInput) {
        viewModelScope.launch {
            try {
                engine.submit(sessionId, input)
            } catch (_: TurnInProgressException) {
                // The screen holds input back while a Turn runs; a double tap lands here and is dropped.
            } catch (_: NextNotAllowedException) {
                // A double tap on Next: the first one already moved the Session on.
            } catch (_: NoActiveProblemException) {
                // The problem was concluded meanwhile; the dock goes away with it.
            }
        }
    }

    private companion object {
        const val DraftDelayMs = 300L

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

        /** The saved shape of a draft; whether the dock is collapsed is not worth saving. */
        fun QuestionDraft.toAnswerDraft() = AnswerDraft(
            values = selected.toList(),
            dontKnow = dontKnow,
            confidence = ConfidenceStops[confidenceIndex].takeIf { confidenceIndex != DefaultConfidenceIndex },
            answer = answer,
            thinking = thinking,
        )

        fun AnswerDraft.toQuestionDraft(toolCallId: String) = QuestionDraft(
            toolCallId = toolCallId,
            selected = values.toSet(),
            dontKnow = dontKnow,
            confidenceIndex = confidence?.let { ConfidenceStops.indexOf(it) }?.takeIf { it >= 0 } ?: DefaultConfidenceIndex,
            answer = answer,
            thinking = thinking,
        )
    }
}
