package io.github.lucaskfp.learnharness.engine.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A Session's message history, in upstream's record shape (pi-ai messages plus an `id`), so rows
 * round-trip to the desktop JSON format and replay to a provider unchanged. See
 * `src/core/native-messages.ts` upstream.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("role")
sealed interface LhMessage {
    val id: String
    val timestamp: Long
}

@Serializable
@SerialName("user")
data class UserMessage(
    override val id: String,
    val content: String,
    override val timestamp: Long,
) : LhMessage

@Serializable
@SerialName("assistant")
data class AssistantMessage(
    override val id: String,
    val content: List<ContentBlock>,
    val api: String = "anthropic-messages",
    val provider: String = "anthropic",
    val model: String,
    val usage: Usage = Usage(),
    val stopReason: String,
    val errorMessage: String? = null,
    override val timestamp: Long,
) : LhMessage {
    val toolCalls: List<ToolCall> get() = content.filterIsInstance<ToolCall>()
}

/** A question call's answer, or a tool's result. [details] keeps the full record for the UI. */
@Serializable
@SerialName("toolResult")
data class ToolResultMessage(
    override val id: String,
    val toolCallId: String,
    val toolName: String,
    val content: List<TextContent>,
    val details: JsonElement? = null,
    val isError: Boolean = false,
    override val timestamp: Long,
) : LhMessage

/**
 * Upstream's app-only marker (`role: "custom"`): scaffolding, not speech. The model reads
 * [content] as a user turn; [details] is the UI payload and never reaches the model.
 */
@Serializable
@SerialName("custom")
data class MarkerMessage(
    override val id: String,
    val customType: String,
    val content: String,
    val display: Boolean = true,
    val details: JsonElement? = null,
    override val timestamp: Long,
) : LhMessage {
    companion object {
        /** The wake that starts the Teacher once the probe has concluded. */
        const val ProbeHandoff = "probe-handoff"
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ContentBlock

@Serializable
@SerialName("text")
data class TextContent(val text: String) : ContentBlock

@Serializable
@SerialName("thinking")
data class ThinkingContent(
    val thinking: String,
    /** The provider's signature (or, for redacted thinking, its opaque payload): needed to replay. */
    val thinkingSignature: String? = null,
    val redacted: Boolean? = null,
) : ContentBlock

@Serializable
@SerialName("toolCall")
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
    /** Set when the provider's argument stream could not be parsed (for example a cut-off call). */
    val argumentsError: String? = null,
) : ContentBlock

@Serializable
data class Usage(
    val input: Int = 0,
    val output: Int = 0,
    val cacheRead: Int = 0,
    val cacheWrite: Int = 0,
    val reasoning: Int? = null,
    val totalTokens: Int = 0,
    val cost: Cost = Cost(),
)

@Serializable
data class Cost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val total: Double = 0.0,
)

/** Stop reasons as upstream stores them. */
object StopReason {
    const val Stop = "stop"
    const val Length = "length"
    const val ToolUse = "toolUse"
    const val Error = "error"
    const val Aborted = "aborted"
}

/** The one JSON configuration for message rows and tool arguments. */
val LhJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}
