package io.github.lucaskfp.learnharness.engine.provider

import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.TextContent
import io.github.lucaskfp.learnharness.engine.model.ThinkingContent
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What listing the account's models told us about a key. */
sealed interface KeyCheck {
    data class Valid(val models: List<String>) : KeyCheck
    data class Invalid(val message: String) : KeyCheck
}

/**
 * Anthropic's Messages API over streaming SSE: text, thinking, tool calls and usage mapped to
 * [ProviderEvent]s. The history is converted to Anthropic's wire shape here (thinking blocks keep
 * their signatures; a thinking block without one is replayed as text, as upstream does).
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val client: OkHttpClient = defaultClient,
) : ModelProvider {

    override fun stream(request: ProviderRequest): Flow<ProviderEvent> = flow {
        val http = Request.Builder()
            .url("$baseUrl/v1/messages")
            .headers(request)
            .post(LhJson.encodeToString(JsonObject.serializer(), body(request)).toRequestBody(JsonMediaType))
            .build()
        val call = client.newCall(http)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val response = try {
            call.execute()
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw ProviderException(e.message ?: "Network error", cause = e)
        }
        response.use {
            if (!it.isSuccessful) throw ProviderException(errorMessage(it.code, it.body.string()), status = it.code)
            val parser = SseParser()
            val source = it.body.source()
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    parser.handle(line.removePrefix("data:").trim()).forEach { event -> emit(event) }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw ProviderException(e.message ?: "Connection lost", cause = e)
            }
            parser.finish()?.let { event -> emit(event) }
        }
    }.flowOn(Dispatchers.IO)

    /** Lists the account's models: the cheap call that tells a valid key from a typo. */
    suspend fun checkKey(): KeyCheck = withContext(Dispatchers.IO) {
        val http = Request.Builder().url("$baseUrl/v1/models?limit=1000").headers(null).get().build()
        try {
            client.newCall(http).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) return@use KeyCheck.Invalid(errorMessage(response.code, text))
                val data = LhJson.parseToJsonElement(text).jsonObject["data"] as? JsonArray
                KeyCheck.Valid(data.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull })
            }
        } catch (e: IOException) {
            KeyCheck.Invalid(e.message ?: "Network error")
        }
    }

    private fun Request.Builder.headers(request: ProviderRequest?): Request.Builder {
        header("x-api-key", apiKey)
        header("anthropic-version", "2023-06-01")
        header("content-type", "application/json")
        if (request != null) header("accept", "text/event-stream")
        return this
    }

    private fun errorMessage(status: Int, body: String): String = try {
        LhJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    } ?: "HTTP $status"

    internal fun body(request: ProviderRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        val thinking = request.thinking && thinkingMode(request.model) != ThinkingMode.None
        val budget = 4096
        put("max_tokens", if (thinkingMode(request.model) == ThinkingMode.Budget && thinking) budget + 8192 else 32_000)
        put("system", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", request.systemPrompt)
                put("cache_control", buildJsonObject { put("type", "ephemeral") })
            })
        })
        put("messages", wireMessages(request.messages))
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                request.tools.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.inputSchema)
                    })
                }
            })
        }
        if (thinking) {
            put("thinking", when (thinkingMode(request.model)) {
                ThinkingMode.Adaptive -> buildJsonObject { put("type", "adaptive"); put("display", "summarized") }
                else -> buildJsonObject { put("type", "enabled"); put("budget_tokens", budget) }
            })
        }
    }

    /** History to Anthropic messages: tool results fold into one user message after their calls. */
    internal fun wireMessages(messages: List<LhMessage>): JsonArray = buildJsonArray {
        var pendingResults = mutableListOf<JsonObject>()
        fun flushResults() {
            if (pendingResults.isEmpty()) return
            add(buildJsonObject { put("role", "user"); put("content", JsonArray(pendingResults)) })
            pendingResults = mutableListOf()
        }
        for (message in messages) {
            when (message) {
                is ToolResultMessage -> pendingResults += buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", message.toolCallId)
                    put("content", message.content.joinToString("\n") { it.text })
                    if (message.isError) put("is_error", true)
                }
                is UserMessage -> {
                    flushResults()
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray { add(textBlock(message.content)) })
                    })
                }
                is AssistantMessage -> {
                    flushResults()
                    val blocks = assistantBlocks(message)
                    if (blocks.isNotEmpty()) add(buildJsonObject { put("role", "assistant"); put("content", JsonArray(blocks)) })
                }
            }
        }
        flushResults()
    }

    private fun assistantBlocks(message: AssistantMessage): List<JsonObject> = message.content.mapNotNull { block ->
        when (block) {
            is TextContent -> block.text.takeIf { it.isNotBlank() }?.let(::textBlock)
            is ThinkingContent -> when {
                block.redacted == true -> buildJsonObject { put("type", "redacted_thinking"); put("data", block.thinkingSignature.orEmpty()) }
                block.thinking.isBlank() && block.thinkingSignature.isNullOrBlank() -> null
                // Without its signature a thinking block cannot be replayed as thinking.
                block.thinkingSignature.isNullOrBlank() -> textBlock(block.thinking)
                else -> buildJsonObject {
                    put("type", "thinking")
                    put("thinking", block.thinking)
                    put("signature", block.thinkingSignature)
                }
            }
            is ToolCall -> buildJsonObject {
                put("type", "tool_use")
                put("id", block.id)
                put("name", block.name)
                put("input", block.arguments)
            }
        }
    }

    private fun textBlock(text: String) = buildJsonObject { put("type", "text"); put("text", text) }

    /** Folds Anthropic's SSE events into [ProviderEvent]s. */
    private class SseParser {
        private class OpenBlock(val type: String, val id: String? = null, val name: String? = null) {
            val json = StringBuilder()
        }

        private val open = mutableMapOf<Int, OpenBlock>()
        private var usage = Usage()
        private var stopReason: String? = null
        private var done = false

        fun handle(data: String): List<ProviderEvent> {
            if (data.isEmpty() || data == "[DONE]") return emptyList()
            val event = try {
                LhJson.parseToJsonElement(data).jsonObject
            } catch (_: Exception) {
                return emptyList()
            }
            return when (event["type"]?.jsonPrimitive?.contentOrNull) {
                "message_start" -> {
                    event["message"]?.jsonObject?.get("usage")?.jsonObject?.let { usage = merge(usage, it) }
                    emptyList()
                }
                "content_block_start" -> {
                    val index = event["index"]!!.jsonPrimitive.int()
                    val block = event["content_block"]!!.jsonObject
                    val type = block["type"]!!.jsonPrimitive.content
                    open[index] = OpenBlock(type, block["id"]?.jsonPrimitive?.contentOrNull, block["name"]?.jsonPrimitive?.contentOrNull)
                    if (type == "redacted_thinking") {
                        listOf(ProviderEvent.RedactedThinking(block["data"]?.jsonPrimitive?.contentOrNull.orEmpty()))
                    } else {
                        emptyList()
                    }
                }
                "content_block_delta" -> {
                    val index = event["index"]!!.jsonPrimitive.int()
                    val delta = event["delta"]!!.jsonObject
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> listOf(ProviderEvent.TextDelta(delta["text"]!!.jsonPrimitive.content))
                        "thinking_delta" -> listOf(ProviderEvent.ThinkingDelta(delta["thinking"]!!.jsonPrimitive.content))
                        "signature_delta" -> listOf(ProviderEvent.ThinkingSignature(delta["signature"]!!.jsonPrimitive.content))
                        "input_json_delta" -> {
                            open[index]?.json?.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            emptyList()
                        }
                        else -> emptyList()
                    }
                }
                "content_block_stop" -> {
                    val block = open.remove(event["index"]!!.jsonPrimitive.int())
                    if (block?.type == "tool_use") listOf(toolCall(block)) else emptyList()
                }
                "message_delta" -> {
                    event["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull?.let { stopReason = it }
                    event["usage"]?.jsonObject?.let { usage = merge(usage, it) }
                    emptyList()
                }
                "message_stop" -> finish()?.let { listOf(it) } ?: emptyList()
                "error" -> {
                    val error = event["error"]?.jsonObject
                    throw ProviderException(error?.get("message")?.jsonPrimitive?.contentOrNull ?: "The model call failed")
                }
                else -> emptyList()
            }
        }

        fun finish(): ProviderEvent.Done? {
            if (done) return null
            done = true
            val reason = when (stopReason) {
                "tool_use" -> StopReason.ToolUse
                "max_tokens" -> StopReason.Length
                "refusal" -> StopReason.Error
                else -> StopReason.Stop
            }
            return ProviderEvent.Done(reason, usage.copy(totalTokens = usage.input + usage.output + usage.cacheRead + usage.cacheWrite))
        }

        private fun toolCall(block: OpenBlock): ProviderEvent.ToolCallComplete {
            val raw = block.json.toString().ifBlank { "{}" }
            return try {
                ProviderEvent.ToolCallComplete(block.id!!, block.name!!, LhJson.parseToJsonElement(raw).jsonObject)
            } catch (e: Exception) {
                ProviderEvent.ToolCallComplete(block.id!!, block.name!!, JsonObject(emptyMap()), argumentsError = e.message ?: "Invalid JSON")
            }
        }

        private fun merge(current: Usage, raw: JsonObject): Usage = current.copy(
            input = raw["input_tokens"]?.jsonPrimitive?.intOrNull ?: current.input,
            output = raw["output_tokens"]?.jsonPrimitive?.intOrNull ?: current.output,
            cacheRead = raw["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: current.cacheRead,
            cacheWrite = raw["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: current.cacheWrite,
        )

        private fun JsonElement.int(): Int = jsonPrimitive.content.toInt()
    }

    internal enum class ThinkingMode { Adaptive, Budget, None }

    companion object {
        private val JsonMediaType = "application/json".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // a streamed response may idle between events
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Newer Claude models decide their own thinking ("adaptive"); older ones take a token
         * budget; the oldest cannot think.
         */
        internal fun thinkingMode(model: String): ThinkingMode = when {
            model.startsWith("claude-2") || (model.startsWith("claude-3-") && !model.startsWith("claude-3-7")) -> ThinkingMode.None
            Regex("claude-(opus|sonnet)-4-[0-5]($|-)").containsMatchIn(model) ||
                Regex("claude-(opus|sonnet)-4($|-\\d{8})").containsMatchIn(model) ||
                model.contains("haiku") || model.startsWith("claude-3-7") -> ThinkingMode.Budget
            else -> ThinkingMode.Adaptive
        }
    }
}
