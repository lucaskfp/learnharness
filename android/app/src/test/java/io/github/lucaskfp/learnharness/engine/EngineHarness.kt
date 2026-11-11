package io.github.lucaskfp.learnharness.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage
import io.github.lucaskfp.learnharness.engine.provider.ModelProvider
import io.github.lucaskfp.learnharness.engine.provider.ProviderEvent
import io.github.lucaskfp.learnharness.engine.provider.ProviderRequest
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import io.github.lucaskfp.learnharness.engine.tools.QuizMechanics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/**
 * A model provider that plays back a script. Each model call consumes the next scripted reply and
 * is recorded, so tests can assert on what the model was sent. Only the model is faked: the
 * Session engine, the real upstream prompts and tool contracts, and Room are all real.
 */
class ScriptedProvider : ModelProvider {
    private val script = ArrayDeque<suspend FlowCollector<ProviderEvent>.(ProviderRequest) -> Unit>()
    val requests = mutableListOf<ProviderRequest>()

    /** The next model call plays [events] and finishes with [stopReason]. */
    fun reply(vararg events: ProviderEvent, stopReason: String? = null) {
        val stop = stopReason ?: if (events.any { it is ProviderEvent.ToolCallComplete }) StopReason.ToolUse else StopReason.Stop
        script += { emitAll(events.toList()); emit(ProviderEvent.Done(stop, Usage(input = 10, output = 5, totalTokens = 15))) }
    }

    /** The next model call runs [block], so a test can pause or fail it mid-stream. */
    fun replyWith(block: suspend FlowCollector<ProviderEvent>.(ProviderRequest) -> Unit) {
        script += block
    }

    override fun stream(request: ProviderRequest): Flow<ProviderEvent> {
        requests += request
        val next = script.removeFirstOrNull() ?: error("The script has no reply for model call ${requests.size}")
        return flow { next(request) }
    }

    private suspend fun FlowCollector<ProviderEvent>.emitAll(events: List<ProviderEvent>) = events.forEach { emit(it) }
}

fun text(value: String) = ProviderEvent.TextDelta(value)

fun thinking(value: String, signature: String? = null): List<ProviderEvent> =
    listOfNotNull(ProviderEvent.ThinkingDelta(value), signature?.let { ProviderEvent.ThinkingSignature(it) })

fun toolCall(id: String, name: String, arguments: JsonObject) = ProviderEvent.ToolCallComplete(id, name, arguments)

/** Arguments of a `probe` call: options are (label, value) pairs. */
fun probeArguments(
    question: String,
    options: List<Pair<String, String>>,
    correct: String,
    explanation: String = "Because.",
    multiSelect: Boolean? = null,
): JsonObject = buildJsonObject {
    put("question", question)
    put("options", optionsJson(options))
    multiSelect?.let { put("multiSelect", it) }
    put("explanation", explanation)
    put("correctAnswer", correct)
}

fun optionsJson(options: List<Pair<String, String>>): JsonElement = JsonArray(
    options.map { (label, value) -> buildJsonObject { put("label", label); put("value", value) } },
)

fun JsonElement.asPrimitive() = this as JsonPrimitive

/** The Session engine over an in-memory Room database, driven the way the UI drives it. */
class EngineHarness(
    val provider: ScriptedProvider = ScriptedProvider(),
    mechanics: QuizMechanics = QuizMechanics.Choice,
    database: LearnHarnessDatabase? = null,
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val database: LearnHarnessDatabase = database ?: Room.inMemoryDatabaseBuilder(context, LearnHarnessDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = AtomicLong(1_000_000)
    val engine: SessionEngine = DefaultSessionEngine(
        database = this.database,
        assets = AndroidAssetSource(context.assets),
        environment = EngineEnvironment(
            provider = { provider },
            clock = { clock.incrementAndGet() },
            quizMechanics = mechanics,
        ),
        scope = scope,
    )

    fun startTeaching(
        goal: String = "I want to understand derivatives",
        language: TeachingLanguage = TeachingLanguage.English,
    ): String = runBlocking {
        engine.start(StartSession(SessionKind.Teaching, goal, "claude-sonnet-5-5", language))
    }

    /** Waits until no Turn is running in the Session, then returns its view. */
    fun settled(sessionId: String): SessionView = runBlocking {
        withTimeout(10_000) { engine.observe(sessionId).first { it != null && !it.turnRunning }!! }
    }

    /** Waits for [condition] on the Session's view. */
    fun awaitView(sessionId: String, condition: (SessionView) -> Boolean): SessionView = runBlocking {
        withTimeout(10_000) { engine.observe(sessionId).first { it != null && condition(it) }!! }
    }

    /**
     * Polls the Session's view until [condition] holds. For a second engine over a database that
     * another engine writes to: it is not told about those writes, so it re-reads.
     */
    fun eventually(sessionId: String, condition: (SessionView) -> Boolean): SessionView = runBlocking {
        withTimeout(10_000) {
            while (true) {
                engine.observe(sessionId).first()?.takeIf(condition)?.let { return@withTimeout it }
                kotlinx.coroutines.delay(20)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    /** Stops the engine as if the app were killed: its running Turn is cancelled; the database stays. */
    fun stopEngine() = scope.cancel()

    fun close() {
        scope.cancel()
        database.close()
    }
}

/** Arguments of a `probe_done` call: the learner assessment, one strand per dependency. */
fun probeDoneArguments(vararg strands: Pair<String, String>): JsonObject = buildJsonObject {
    put("assessment", buildJsonObject {
        put("strands", JsonArray(strands.map { (strand, assessment) ->
            buildJsonObject { put("strand", strand); put("assessment", assessment) }
        }))
    })
}

/** Arguments of a `plan_init` call: a mermaid block. */
fun planInitArguments(mermaid: String): JsonObject = buildJsonObject { put("mermaid", mermaid) }

/** Arguments of a Teacher `quiz` call tagged with the Plan node it is about. */
fun quizArguments(
    nodeId: String,
    question: String,
    options: List<Pair<String, String>>,
    correct: String,
    explanation: String = "Because.",
): JsonObject = buildJsonObject {
    put("type", "check")
    put("nodeId", nodeId)
    put("question", question)
    put("options", optionsJson(options))
    put("explanation", explanation)
    put("correctAnswer", correct)
}

val TwoNodePlan = "graph TD\n  slope[The slope idea] --> rule[The power rule]"
