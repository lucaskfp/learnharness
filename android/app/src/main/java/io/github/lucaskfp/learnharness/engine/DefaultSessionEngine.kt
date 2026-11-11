package io.github.lucaskfp.learnharness.engine

import androidx.room.withTransaction
import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.ContentBlock
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.LhMessage
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
import io.github.lucaskfp.learnharness.engine.store.SessionRow
import io.github.lucaskfp.learnharness.engine.tools.ProberTools
import io.github.lucaskfp.learnharness.engine.tools.QuizMechanics
import io.github.lucaskfp.learnharness.engine.tools.ToolContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID

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
    private val tools = ProberTools(contracts, environment.quizMechanics)

    private val running = MutableStateFlow<Set<String>>(emptySet())
    private val live = MutableStateFlow<Map<String, List<ContentBlock>>>(emptyMap())

    /** Bumped after every change to saved state or to which Turns run; observers re-read on it. */
    private val revision = MutableStateFlow(0L)

    override suspend fun start(request: StartSession): String {
        require(request.kind == SessionKind.Teaching) { "Practice Sessions are not supported yet" }
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
        } catch (e: Throwable) {
            release(sessionId)
            throw e
        }
        launchTurn(sessionId)
    }

    override fun observe(sessionId: String): Flow<SessionView?> = combine(revision, live) { _, _ -> }
        .map {
            // Read the running flag first: a Turn saves its output and clears its live output
            // before it is released, so a Session seen idle is read with everything it saved.
            val turnRunning = sessionId in running.value
            val liveNow = live.value
            val session = database.sessions().get(sessionId) ?: return@map null
            val messages = loadHistory(sessionId)
            val projection = project(messages)
            SessionView(
                id = session.id,
                goal = session.title,
                kind = SessionKind.valueOf(session.kind),
                phase = SessionPhase.Probing,
                transcript = projection.transcript + liveEntries(liveNow[sessionId].orEmpty()),
                // The learner answers between Turns, never during one.
                pendingQuestion = if (turnRunning) null else projection.pending?.let {
                    PendingQuestionView(it.toolCallId, it.tool, it.kind, it.question, it.details, it.options, it.multiSelect, it.freeText)
                },
                turnRunning = turnRunning,
                error = (messages.lastOrNull() as? AssistantMessage)?.takeIf { it.stopReason == StopReason.Error }?.errorMessage,
            )
        }
        .distinctUntilChanged()

    override fun sessions(): Flow<List<SessionSummary>> = revision.map {
        val runningNow = running.value
        database.sessions().all().map {
            SessionSummary(it.id, it.title, SessionKind.valueOf(it.kind), SessionPhase.Probing, it.updatedAt, it.id in runningNow)
        }
    }.distinctUntilChanged()

    override suspend fun delete(sessionId: String) {
        database.sessions().delete(sessionId)
        revision.value++
    }

    // ─── the Turn ───────────────────────────────────────────────────────────────────────

    private fun launchTurn(sessionId: String) {
        scope.launch {
            try {
                runTurn(sessionId)
            } finally {
                live.value = live.value - sessionId
                release(sessionId)
            }
        }
    }

    /**
     * Builds the agent from the saved history and loops: call the model, run its tools, call again,
     * until it stops or poses a question. Everything the Turn produced is saved once, at settlement,
     * including partial output when the Turn is cancelled or fails.
     */
    private suspend fun runTurn(sessionId: String) {
        val session = database.sessions().get(sessionId) ?: return
        val system = prompts.prober(TeachingLanguage.valueOf(session.teachingLanguage))
        var history = loadHistory(sessionId)
        val produced = mutableListOf<LhMessage>()
        try {
            val provider = environment.provider(session.provider)
            while (true) {
                val request = ProviderRequest(
                    model = session.model,
                    systemPrompt = system,
                    messages = toLlmMessages(history, contracts.texts.interrupted, environment.clock()),
                    tools = tools.specs,
                    thinking = true,
                )
                val assistant = callModel(sessionId, provider, request, produced)
                produced += assistant
                history = history + assistant
                if (assistant.stopReason != StopReason.ToolUse) break

                var posedQuestion = false
                for (call in assistant.toolCalls) {
                    val outcome = tools.execute(call)
                    if (outcome.terminates) {
                        // The "question posed" placeholder is never saved: the learner's answer is the result.
                        posedQuestion = true
                        continue
                    }
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
        } catch (e: CancellationException) {
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
            val partial = assistantOf(request.model, builder, StopReason.Aborted, usage, null)
            if (partial.content.isNotEmpty()) produced += partial
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

    private fun release(sessionId: String) = synchronized(lock) {
        running.value = running.value - sessionId
        revision.value++
    }
}

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
