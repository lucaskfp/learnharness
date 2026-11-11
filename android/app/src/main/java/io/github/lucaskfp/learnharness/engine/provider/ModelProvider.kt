package io.github.lucaskfp.learnharness.engine.provider

import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.tools.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** One model call. [messages] is the history as the provider should see it. */
data class ProviderRequest(
    val model: String,
    val systemPrompt: String,
    val messages: List<LhMessage>,
    val tools: List<ToolSpec>,
    /** Ask the model to think and stream its reasoning, where it can. */
    val thinking: Boolean,
)

/** What a streaming model call emits, in order. Consecutive deltas of one kind form one block. */
sealed interface ProviderEvent {
    data class TextDelta(val text: String) : ProviderEvent
    data class ThinkingDelta(val text: String) : ProviderEvent

    /** Closes the current thinking block with the provider's signature. */
    data class ThinkingSignature(val signature: String) : ProviderEvent

    /** A thinking block the provider withheld; [data] is the opaque payload to replay. */
    data class RedactedThinking(val data: String) : ProviderEvent

    data class ToolCallComplete(
        val id: String,
        val name: String,
        val arguments: JsonObject,
        val argumentsError: String? = null,
    ) : ProviderEvent

    data class Done(val stopReason: String, val usage: Usage) : ProviderEvent
}

/** A failed model call: a network failure, or an error the provider reported. */
class ProviderException(message: String, val status: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The provider port: one streaming-model interface carrying text, reasoning, tool calls and
 * usage. Anthropic is the first implementation; tests script this port with a fake.
 */
fun interface ModelProvider {
    fun stream(request: ProviderRequest): Flow<ProviderEvent>
}
