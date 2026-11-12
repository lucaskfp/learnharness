package io.github.lucaskfp.learnharness.engine

import androidx.room.withTransaction
import io.github.lucaskfp.learnharness.engine.model.string
import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.ContentBlock
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.MarkerMessage
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.TextContent
import io.github.lucaskfp.learnharness.engine.model.ThinkingContent
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import io.github.lucaskfp.learnharness.engine.prompts.PromptComposer
import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage
import io.github.lucaskfp.learnharness.engine.provider.ModelProvider
import io.github.lucaskfp.learnharness.engine.provider.ProviderEvent
import io.github.lucaskfp.learnharness.engine.provider.ProviderRequest
import io.github.lucaskfp.learnharness.engine.question.buildAnswer
import io.github.lucaskfp.learnharness.engine.question.compactAnswerForModel
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import io.github.lucaskfp.learnharness.engine.store.MessageRow
import io.github.lucaskfp.learnharness.engine.store.DraftRow
import io.github.lucaskfp.learnharness.engine.store.SessionRow
import io.github.lucaskfp.learnharness.engine.store.SteerRow
import io.github.lucaskfp.learnharness.engine.tools.AgentKind
import io.github.lucaskfp.learnharness.engine.tools.AgentTools
import io.github.lucaskfp.learnharness.engine.tools.QuizMechanics
import io.github.lucaskfp.learnharness.engine.tools.ToolContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The things outside the device the engine depends on, so tests can replace them. */
class EngineEnvironment(
    /** The model provider for a provider id (Anthropic is the only one so far). */
    val provider: suspend (providerId: String) -> ModelProvider,
    val clock: () -> Long = System::currentTimeMillis,
    val quizMechanics: QuizMechanics = QuizMechanics.Choice,
)

/**
 * The Session engine. A Turn builds a fresh agent from the saved history, may make several model
 * calls and run tools, and settles by saving its output. Learner input is saved before inference.
 * One Turn runs per Session at a time.
 */
class DefaultSessionEngine(
    private val database: LearnHarnessDatabase,
    private val assets: AssetSource,
    private val environment: EngineEnvironment,
    private val scope: CoroutineScope,
) : SessionEngine {
    private val contracts = ToolContracts.load(assets)
    private val prompts = PromptComposer(assets)
    private val tools = AgentTools(contracts, environment.quizMechanics)

    private val running = MutableStateFlow<Set<String>>(emptySet())
    private val turns = ConcurrentHashMap<String, Job>()
    private val live = MutableStateFlow<Map<String, List<ContentBlock>>>(emptyMap())

    /** Bumped after every change to saved state or to which Turns run; observers re-read on it. */
    private val revision = MutableStateFlow(0L)

    override suspend fun start(request: StartSession): String {
        require(request.goal.isNotBlank()) { "A Session needs a goal" }
        val now = environment.clock()
        val sessionId = "chat_${now.toString(36)}_${UUID.randomUUID().toString().take(8)}"
        val row = SessionRow(
            id = sessionId,
            title = titleFromPrompt(request.goal),
            kind = request.kind.name,
            provider = "anthropic",
            model = request.model,
            teachingLanguage = request.teachingLanguage.name,
            createdAt = now,
            updatedAt = now,
        )
        acquire(sessionId)
        try {
            database.withTransaction {
                database.sessions().insert(row)
                database.messages().insert(encode(sessionId, UserMessage(newId("msg"), request.goal.trim(), now)))
            }
        } catch (e: Throwable) {
            release(sessionId)
            throw e
        }
        launchTurn(sessionId)
        return sessionId
    }

    override suspend fun submit(sessionId: String, input: LearnerInput) {
        acquire(sessionId)
        try {
            val now = environment.clock()
            val history = loadHistory(sessionId)
            val message: LhMessage = when (input) {
                is LearnerInput.Message -> {
                    require(input.text.isNotBlank()) { "A message cannot be empty" }
                    UserMessage(newId("msg"), input.text.trim(), now)
                }
                LearnerInput.Next -> {
                    val kind = database.sessions().get(sessionId)?.kind?.let(SessionKind::valueOf)
                    if (!nextAllowed(kind, history)) throw NextNotAllowedException(sessionId)
                    MarkerMessage(newId("msg"), MarkerMessage.PracticeNext, contracts.texts.nextProblem, timestamp = now)
                }
                is LearnerInput.Thinking -> {
                    require(input.text.isNotBlank()) { "Thinking cannot be empty" }
                    if (activeProblemOf(history)?.id != input.problemCallId) throw NoActiveProblemException(input.problemCallId)
                    MarkerMessage(
                        id = newId("msg"),
                        customType = MarkerMessage.PracticeThinking,
                        content = "${contracts.texts.thinkingLabel}:\n${input.text.trim()}",
                        details = buildJsonObject {
                            put("problemCallId", input.problemCallId)
                            put("text", input.text.trim())
                        },
                        timestamp = now,
                    )
                }
                is LearnerInput.Answer -> {
                    val armed = project(history).pending?.takeIf { it.toolCallId == input.toolCallId }
                        ?: throw NoPendingQuestionException(input.toolCallId)
                    val answer = buildAnswer(armed, input.request, Instant.ofEpochMilli(now).toString())
                    ToolResultMessage(
                        id = newId("msg"),
                        toolCallId = armed.toolCallId,
                        toolName = armed.tool,
                        content = listOf(TextContent(LhJson.encodeToString(JsonObject.serializer(), compactAnswerForModel(answer)))),
                        details = LhJson.encodeToJsonElement(io.github.lucaskfp.learnharness.engine.question.QuestionAnswerData.serializer(), answer),
                        isError = false,
                        timestamp = now,
                    )
                }
            }
            persist(sessionId, listOf(message), now)
            // What was sent is no longer a draft.
            when (input) {
                is LearnerInput.Answer -> database.drafts().delete(sessionId, AnswerDraftPrefix + input.toolCallId)
                is LearnerInput.Thinking -> database.drafts().delete(sessionId, AnswerDraftPrefix + input.problemCallId)
                is LearnerInput.Message -> database.drafts().delete(sessionId, ComposerDraft)
                LearnerInput.Next -> Unit
            }
            // A sibling question still waits: the answer is saved, and the last answer wakes the
            // model with every result right after the message that asked.
            if (message is ToolResultMessage && hasUnresolvedQuestion(history + message)) {
                release(sessionId)
                return
            }
        } catch (e: Throwable) {
            release(sessionId)
            throw e
        }
        launchTurn(sessionId)
    }

    override suspend fun abort(sessionId: String) {
        // An input that has just claimed the Session may not have launched its Turn yet: wait for it.
        while (true) {
            turns[sessionId]?.let { it.cancelAndJoin(); return }
            if (sessionId !in running.value) return
            delay(5)
        }
    }

    override suspend fun retry(sessionId: String) {
        acquire(sessionId)
        val allowed = try {
            endsInRetryableFailure(loadHistory(sessionId))
        } catch (e: Throwable) {
            release(sessionId)
            throw e
        }
        if (!allowed) {
            release(sessionId)
            throw RetryNotAllowedException(sessionId)
        }
        // The provider never reads failed responses (toLlmMessages), so the Turn starts from the input before them.
        launchTurn(sessionId)
    }

    override suspend fun steer(sessionId: String, text: String) {
        require(text.isNotBlank()) { "A steering message cannot be empty" }
        database.steers().insert(SteerRow(newId("steer"), sessionId, text.trim(), environment.clock()))
        database.drafts().delete(sessionId, ComposerDraft)
        revision.value++
        wakeForSteers(sessionId)
    }

    /**
     * Runs a Turn for queued steers when the Session is idle and no question waits: a steer sent as
     * a Turn ended, or with no Turn at all. A queue behind a pending question waits for its answer.
     */
    private suspend fun wakeForSteers(sessionId: String) {
        if (!tryAcquire(sessionId)) return
        val wake = try {
            database.steers().all(sessionId).isNotEmpty() && project(loadHistory(sessionId)).pending == null
        } catch (e: Throwable) {
            release(sessionId)
            throw e
        }
        if (wake) launchTurn(sessionId) else release(sessionId)
    }

    override suspend fun drafts(sessionId: String): SessionDrafts {
        val rows = database.drafts().all(sessionId)
        return SessionDrafts(
            composer = rows.firstOrNull { it.key == ComposerDraft }?.json?.let { LhJson.decodeFromString(String.serializer(), it) }.orEmpty(),
            answers = rows.filter { it.key.startsWith(AnswerDraftPrefix) }.associate {
                it.key.removePrefix(AnswerDraftPrefix) to LhJson.decodeFromString(AnswerDraft.serializer(), it.json)
            },
        )
    }

    override suspend fun saveComposerDraft(sessionId: String, text: String) {
        if (text.isBlank()) database.drafts().delete(sessionId, ComposerDraft)
        else database.drafts().put(DraftRow(sessionId, ComposerDraft, LhJson.encodeToString(String.serializer(), text)))
    }

    override suspend fun saveAnswerDraft(sessionId: String, toolCallId: String, draft: AnswerDraft?) {
        val key = AnswerDraftPrefix + toolCallId
        if (draft == null || draft == AnswerDraft()) database.drafts().delete(sessionId, key)
        else database.drafts().put(DraftRow(sessionId, key, LhJson.encodeToString(AnswerDraft.serializer(), draft)))
    }

    override fun observe(sessionId: String): Flow<SessionView?> = combine(revision, live) { _, _ -> }
        .map {
            // Read the running flag first: a Turn saves its output and clears its live output
            // before it is released, so a Session seen idle is read with everything it saved.
            val turnRunning = sessionId in running.value
            val liveNow = live.value
            val session = database.sessions().get(sessionId) ?: return@map null
            val kind = SessionKind.valueOf(session.kind)
            val messages = loadHistory(sessionId)
            val projection = project(messages)
            SessionView(
                id = session.id,
                goal = session.title,
                kind = kind,
                phase = phaseOf(kind, messages),
                plan = planViewOf(messages),
                transcript = projection.transcript + liveEntries(liveNow[sessionId].orEmpty()),
                // The learner answers between Turns, never during one.
                nextAllowed = !turnRunning && nextAllowed(kind, messages),
                problemConcluded = kind == SessionKind.Practice &&
                    practicePhaseOf(messages) == SessionPhase.ProblemConcluded,
                activeProblem = if (turnRunning) null else activeProblemOf(messages)?.let {
                    ActiveProblemView(it.id, it.arguments.string("statement"))
                },
                pendingQuestion = if (turnRunning) null else projection.pending?.let {
                    val (position, siblings) = siblingPlace(messages, it.toolCallId)
                    PendingQuestionView(
                        it.toolCallId, it.tool, it.kind, it.question, it.details, it.options, it.multiSelect, it.freeText,
                        it.questionType, position, siblings,
                    )
                },
                turnRunning = turnRunning,
                failure = (messages.lastOrNull() as? AssistantMessage)?.takeIf { it.stopReason == StopReason.Error }?.let {
                    FailureView(it.errorMessage ?: "", endsInRetryableFailure(messages))
                },
                queuedSteers = database.steers().all(sessionId).map { QueuedSteer(it.id, it.text) },
            )
        }
        .distinctUntilChanged()

    override fun sessions(): Flow<List<SessionSummary>> = revision.map {
        val runningNow = running.value
        database.sessions().all().map {
            SessionSummary(it.id, it.title, SessionKind.valueOf(it.kind), phaseCached(it), it.updatedAt, it.id in runningNow)
        }
    }.distinctUntilChanged()

    /** Phases by Session, keyed to the `updatedAt` they were read at: the list reloads a history only when it changed. */
    private val phases = ConcurrentHashMap<String, Pair<Long, SessionPhase>>()

    private suspend fun phaseCached(row: SessionRow): SessionPhase {
        phases[row.id]?.takeIf { it.first == row.updatedAt }?.let { return it.second }
        return phaseOf(SessionKind.valueOf(row.kind), loadHistory(row.id)).also { phases[row.id] = row.updatedAt to it }
    }

    override suspend fun delete(sessionId: String) {
        phases.remove(sessionId)
        database.sessions().delete(sessionId)
        revision.value++
    }

    // ─── the Turn ───────────────────────────────────────────────────────────────────────

    private fun launchTurn(sessionId: String) {
        // Undispatched: the Turn registers itself before launch returns, so an abort always finds it,
        // and its body always runs, so it always settles.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val job = coroutineContext.job
            turns[sessionId] = job
            val endedCleanly = try {
                runTurn(sessionId)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { markAborted(sessionId) }
                throw e
            } finally {
                turns.remove(sessionId, job)
                live.value = live.value - sessionId
                release(sessionId)
            }
            // A steer that arrived after the Turn's last boundary is not stranded.
            if (endedCleanly) wakeForSteers(sessionId)
        }
    }

    /**
     * Makes sure a cancelled Turn ends in an aborted message. The Turn adds it itself, with its
     * partial output; this covers a cancellation that came before the Turn had begun its work.
     */
    private suspend fun markAborted(sessionId: String) {
        val last = loadHistory(sessionId).lastOrNull()
        if (last is AssistantMessage && last.stopReason == StopReason.Aborted) return
        val session = database.sessions().get(sessionId) ?: return
        persist(
            sessionId,
            listOf(AssistantMessage(newId("msg"), emptyList(), model = session.model, stopReason = StopReason.Aborted, timestamp = environment.clock())),
            environment.clock(),
        )
    }

    /**
     * Builds the agent from the saved history and loops: call the model, run its tools, call again,
     * until it stops or poses a question. Everything the Turn produced is saved once, at settlement,
     * including partial output when the Turn is cancelled or fails. Steers are saved as they are
     * delivered, together with everything the Turn produced before them, to keep the order.
     *
     * @return whether the Turn ended with a clean stop (not on a question, a failure or an abort).
     */
    private suspend fun runTurn(sessionId: String): Boolean {
        val session = database.sessions().get(sessionId) ?: return false
        val language = TeachingLanguage.valueOf(session.teachingLanguage)
        val sessionKind = SessionKind.valueOf(session.kind)
        var history = loadHistory(sessionId)
        val produced = mutableListOf<LhMessage>()
        try {
            val provider = environment.provider(session.provider)
            while (true) {
                // The agent is chosen from the history at the start of each stretch: after a
                // probe_done the Prober still finishes its Turn (its closing sentence), and the
                // Teacher takes over from the next stretch.
                val kind = agentFor(sessionKind, history)
                if (kind == AgentKind.Teacher && history.none { it is MarkerMessage && it.customType == MarkerMessage.ProbeHandoff }) {
                    // Also covers a handoff cut short (a failed closing call): the Teacher is then
                    // woken by the learner's next input.
                    val handoff = MarkerMessage(
                        id = "steer_probe_$sessionId",
                        customType = MarkerMessage.ProbeHandoff,
                        content = contracts.texts.probeHandoff,
                        timestamp = environment.clock(),
                    )
                    produced += handoff
                    history = history + handoff
                }
                // The one-active-problem gate is seeded once per Turn from the saved history.
                val gate = if (kind == AgentKind.Practitioner) PracticeGate(activeProblemOf(history) != null) else null
                val system = when (kind) {
                    AgentKind.Prober -> prompts.prober(language)
                    AgentKind.Teacher -> prompts.teacher(language)
                    AgentKind.Practitioner -> prompts.practitioner(language)
                }
                var posedQuestion = false
                while (true) {
                    // The safe boundary: before every model call, never between a call and its results.
                    history = history + deliverSteers(sessionId, produced)
                    val request = ProviderRequest(
                        model = session.model,
                        systemPrompt = system,
                        messages = toLlmMessages(history, contracts.texts.interrupted, environment.clock()),
                        tools = tools.specs(kind),
                        thinking = true,
                    )
                    val assistant = callModel(sessionId, provider, request, produced)
                    produced += assistant
                    history = history + assistant
                    if (assistant.stopReason != StopReason.ToolUse) break

                    for (call in assistant.toolCalls) {
                        val outcome = tools.execute(kind, call, history, gate)
                        if (outcome.terminates) posedQuestion = true
                        // The "question posed" placeholder is never saved: the learner's answer is the result.
                        if (!outcome.persist) continue
                        val result = ToolResultMessage(
                            id = newId("msg"),
                            toolCallId = call.id,
                            toolName = call.name,
                            content = listOf(TextContent(outcome.text)),
                            details = outcome.details,
                            isError = outcome.isError,
                            timestamp = environment.clock(),
                        )
                        produced += result
                        history = history + result
                    }
                    if (posedQuestion) break
                }
                // A Prober stretch that concluded the probe and ended cleanly wakes the Teacher at
                // once, so the learner goes from assessment to Plan without prompting.
                val ended = history.last()
                val stopped = !posedQuestion && ended is AssistantMessage && ended.stopReason == StopReason.Stop
                val concluded = stopped && kind == AgentKind.Prober && hasProbeDone(history)
                // A Turn about to stop with steers waiting makes one more call to answer them.
                val steered = stopped && database.steers().all(sessionId).isNotEmpty()
                if (!concluded && !steered) return stopped
            }
        } catch (e: CancellationException) {
            // An aborted Turn always ends in an aborted message, even one cut off before any
            // output or between model calls: it marks where the learner stopped the Turn.
            val last = produced.lastOrNull()
            if (last !is AssistantMessage || last.stopReason != StopReason.Aborted) {
                produced += AssistantMessage(
                    id = newId("msg"),
                    content = emptyList(),
                    model = session.model,
                    stopReason = StopReason.Aborted,
                    timestamp = environment.clock(),
                )
            }
            throw e
        } catch (e: Exception) {
            // A failure before or between model calls (for example no API key) is recorded like a failed call.
            produced += AssistantMessage(
                id = newId("msg"),
                content = emptyList(),
                model = session.model,
                stopReason = StopReason.Error,
                errorMessage = e.message ?: e::class.java.simpleName,
                timestamp = environment.clock(),
            )
        } finally {
            if (produced.isNotEmpty()) {
                withContext(NonCancellable) { persist(sessionId, produced, environment.clock()) }
            }
        }
        return false
    }

    /** One model call: streams events into an assistant message, surfacing it live as it grows. */
    private suspend fun callModel(
        sessionId: String,
        provider: ModelProvider,
        request: ProviderRequest,
        produced: MutableList<LhMessage>,
    ): AssistantMessage {
        val builder = AssistantBuilder()
        var stopReason = StopReason.Stop
        var usage = Usage()
        var failure: String? = null
        try {
            provider.stream(request).collect { event ->
                if (event is ProviderEvent.Done) {
                    stopReason = event.stopReason
                    usage = event.usage
                } else {
                    builder.add(event)
                    live.value = live.value + (sessionId to builder.snapshot())
                }
            }
        } catch (e: CancellationException) {
            // A cancelled call keeps what it generated; the Turn saves it at settlement.
            produced += assistantOf(request.model, builder, StopReason.Aborted, usage, null)
            throw e
        } catch (e: Exception) {
            stopReason = StopReason.Error
            failure = e.message ?: e::class.java.simpleName
        }
        return assistantOf(request.model, builder, stopReason, usage, failure)
    }

    private fun assistantOf(model: String, builder: AssistantBuilder, stopReason: String, usage: Usage, failure: String?) =
        AssistantMessage(
            id = newId("msg"),
            content = builder.snapshot(),
            model = model,
            usage = usage,
            stopReason = stopReason,
            errorMessage = failure,
            timestamp = environment.clock(),
        )

    // ─── persistence and bookkeeping ────────────────────────────────────────────────────

    /**
     * Delivers the queued steers: each becomes a learner message under its steer id, saved with
     * what the Turn produced so far while its queue row is deleted, in one transaction.
     */
    private suspend fun deliverSteers(sessionId: String, produced: MutableList<LhMessage>): List<LhMessage> {
        val queued = database.steers().all(sessionId)
        if (queued.isEmpty()) return emptyList()
        val now = environment.clock()
        val delivered = queued.map { UserMessage(it.id, it.text, now) }
        database.withTransaction {
            (produced + delivered).forEach { database.messages().insert(encode(sessionId, it)) }
            database.steers().delete(queued.map { it.id })
            database.sessions().touch(sessionId, now)
        }
        produced.clear()
        revision.value++
        return delivered
    }

    private suspend fun loadHistory(sessionId: String): List<LhMessage> =
        database.messages().all(sessionId).map(::decode)

    private suspend fun persist(sessionId: String, messages: List<LhMessage>, at: Long) {
        database.withTransaction {
            messages.forEach { database.messages().insert(encode(sessionId, it)) }
            database.sessions().touch(sessionId, at)
        }
        revision.value++
    }

    private fun encode(sessionId: String, message: LhMessage) =
        MessageRow(sessionId = sessionId, messageId = message.id, json = LhJson.encodeToString(LhMessage.serializer(), message))

    private fun decode(row: MessageRow): LhMessage = LhJson.decodeFromString(LhMessage.serializer(), row.json)

    private fun newId(prefix: String) = "${prefix}_${environment.clock().toString(36)}_${UUID.randomUUID().toString().take(8)}"

    private val lock = Any()

    private fun acquire(sessionId: String) = synchronized(lock) {
        if (sessionId in running.value) throw TurnInProgressException(sessionId)
        running.value = running.value + sessionId
        revision.value++
    }

    /** Like [acquire], but reports a running Turn instead of throwing. */
    private fun tryAcquire(sessionId: String): Boolean = synchronized(lock) {
        if (sessionId in running.value) return false
        running.value = running.value + sessionId
        revision.value++
        true
    }

    private fun release(sessionId: String) = synchronized(lock) {
        running.value = running.value - sessionId
        revision.value++
    }
}

private const val ComposerDraft = "composer"
private const val AnswerDraftPrefix = "answer:"

/** Folds provider events into content blocks: consecutive deltas of one kind form one block. */
private class AssistantBuilder {
    private val blocks = mutableListOf<ContentBlock>()

    fun add(event: ProviderEvent) {
        when (event) {
            is ProviderEvent.TextDelta -> {
                val last = blocks.lastOrNull()
                if (last is TextContent) blocks[blocks.lastIndex] = TextContent(last.text + event.text)
                else blocks += TextContent(event.text)
            }
            is ProviderEvent.ThinkingDelta -> {
                val last = blocks.lastOrNull()
                if (last is ThinkingContent && last.thinkingSignature == null) {
                    blocks[blocks.lastIndex] = last.copy(thinking = last.thinking + event.text)
                } else {
                    blocks += ThinkingContent(event.text)
                }
            }
            is ProviderEvent.ThinkingSignature -> {
                val last = blocks.lastOrNull()
                if (last is ThinkingContent) blocks[blocks.lastIndex] = last.copy(thinkingSignature = event.signature)
                else blocks += ThinkingContent("", event.signature)
            }
            is ProviderEvent.RedactedThinking -> blocks += ThinkingContent("[Reasoning redacted]", event.data, redacted = true)
            is ProviderEvent.ToolCallComplete -> blocks += ToolCall(event.id, event.name, event.arguments, event.argumentsError)
            is ProviderEvent.Done -> Unit
        }
    }

    fun snapshot(): List<ContentBlock> = blocks.toList()
}

/** Upstream's `titleFromPrompt` (`src/core/contracts/graph.ts`): one line, cut at a word boundary. */
internal fun titleFromPrompt(prompt: String, max: Int = 72): String {
    val oneLine = prompt.replace(Regex("\\s+"), " ").trim()
    if (oneLine.length <= max) return oneLine
    var cut = -1
    for (i in max downTo 1) {
        if (oneLine[i - 1] == ' ') {
            cut = i - 1
            break
        }
    }
    if (cut < 0) cut = max
    return oneLine.substring(0, cut) + "…"
}
