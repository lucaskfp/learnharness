package io.github.lucaskfp.learnharness.engine.provider

import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.TextContent
import io.github.lucaskfp.learnharness.engine.model.ThinkingContent
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.tools.ToolSpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The Anthropic adapter against a local server speaking its SSE wire format. */
class AnthropicProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: AnthropicProvider

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        provider = AnthropicProvider("sk-ant-test", baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun sse(vararg events: Pair<String, String>) = MockResponse()
        .setHeader("content-type", "text/event-stream")
        .setBody(events.joinToString("") { (type, data) -> "event: $type\ndata: $data\n\n" })

    private val request = ProviderRequest(
        model = "claude-sonnet-5-5",
        systemPrompt = "You are the agent.",
        messages = listOf(UserMessage("u1", "Teach me", 1)),
        tools = listOf(ToolSpec("probe", "Ask.", buildJsonObject { put("type", "object") })),
        thinking = true,
    )

    @Test
    fun streamsThinkingTextAndAToolCallWithUsage() {
        server.enqueue(
            sse(
                "message_start" to """{"type":"message_start","message":{"usage":{"input_tokens":25,"cache_read_input_tokens":3,"output_tokens":1}}}""",
                "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Hmm. "}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig-abc"}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":0}""",
                "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Let's "}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"begin."}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":1}""",
                "content_block_start" to """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_9","name":"probe","input":{}}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"question\":"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"Why?\"}"}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":2}""",
                "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )

        val events = runBlocking { provider.stream(request).toList() }

        assertEquals(
            listOf(
                ProviderEvent.ThinkingDelta("Hmm. "),
                ProviderEvent.ThinkingSignature("sig-abc"),
                ProviderEvent.TextDelta("Let's "),
                ProviderEvent.TextDelta("begin."),
                ProviderEvent.ToolCallComplete("toolu_9", "probe", buildJsonObject { put("question", "Why?") }),
            ),
            events.dropLast(1),
        )
        val done = events.last() as ProviderEvent.Done
        assertEquals("toolUse", done.stopReason)
        assertEquals(25, done.usage.input)
        assertEquals(42, done.usage.output)
        assertEquals(3, done.usage.cacheRead)
        assertEquals(70, done.usage.totalTokens)
    }

    @Test
    fun sendsTheRequestInAnthropicsWireShape() {
        server.enqueue(sse("message_stop" to """{"type":"message_stop"}"""))

        runBlocking { provider.stream(request).toList() }

        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        val body = LhJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("claude-sonnet-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        assertEquals("You are the agent.", body["system"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("probe", body["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        val message = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals("Teach me", message["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun replaysHistoryWithSignedThinkingToolCallsAndTheirResults() {
        val history = listOf(
            UserMessage("u1", "Teach me", 1),
            AssistantMessage(
                id = "a1", model = "claude-sonnet-5-5", stopReason = "toolUse", timestamp = 2,
                content = listOf(
                    ThinkingContent("Plan", thinkingSignature = "sig-1"),
                    ThinkingContent("Unsigned musing"),
                    TextContent("Question time."),
                    ToolCall("toolu_1", "probe", buildJsonObject { put("question", "Why?") }),
                ),
            ),
            ToolResultMessage("r1", "toolu_1", "probe", listOf(TextContent("""{"kind":"quiz"}""")), timestamp = 3),
        )

        val wire = provider.wireMessages(history).map { it.jsonObject }

        assertEquals(listOf("user", "assistant", "user"), wire.map { it["role"]!!.jsonPrimitive.content })
        val assistant = wire[1]["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("thinking", "text", "text", "tool_use"), assistant.map { it["type"]!!.jsonPrimitive.content })
        assertEquals("sig-1", assistant[0]["signature"]!!.jsonPrimitive.content)
        assertEquals("Unsigned musing", assistant[1]["text"]!!.jsonPrimitive.content)
        val result = wire[2]["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("toolu_1", result["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun aCutOffToolCallCarriesAnArgumentsError() {
        server.enqueue(
            sse(
                "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"probe","input":{}}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"question\":\"Wh"}}""",
                "content_block_stop" to """{"type":"content_block_stop","index":0}""",
                "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":9}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )

        val events = runBlocking { provider.stream(request).toList() }

        val call = events.filterIsInstance<ProviderEvent.ToolCallComplete>().single()
        assertTrue(call.argumentsError != null)
        assertEquals("length", (events.last() as ProviderEvent.Done).stopReason)
    }

    @Test
    fun anHttpErrorCarriesTheProvidersMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))

        try {
            runBlocking { provider.stream(request).toList() }
            fail("expected a ProviderException")
        } catch (e: ProviderException) {
            assertEquals("invalid x-api-key", e.message)
            assertEquals(401, e.status)
        }
    }

    @Test
    fun anErrorEventMidStreamFailsTheCall() {
        server.enqueue(sse("error" to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))

        try {
            runBlocking { provider.stream(request).toList() }
            fail("expected a ProviderException")
        } catch (e: ProviderException) {
            assertEquals("Overloaded", e.message)
        }
    }

    @Test
    fun checkingAKeyListsTheModelsItCanUse() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"claude-opus-5-5"},{"id":"claude-haiku-5-5"}]}"""))

        val check = runBlocking { provider.checkKey() }

        assertEquals(KeyCheck.Valid(listOf("claude-opus-5-5", "claude-haiku-5-5")), check)
        assertEquals("sk-ant-test", server.takeRequest().getHeader("x-api-key"))
    }

    @Test
    fun checkingAWrongKeyIsInvalid() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid x-api-key"}}"""))

        assertEquals(KeyCheck.Invalid("invalid x-api-key"), runBlocking { provider.checkKey() })
    }

    @Test
    fun thinkingFollowsTheModelGeneration() {
        assertEquals(AnthropicProvider.ThinkingMode.Adaptive, AnthropicProvider.thinkingMode("claude-sonnet-5-5"))
        assertEquals(AnthropicProvider.ThinkingMode.Adaptive, AnthropicProvider.thinkingMode("claude-opus-4-7"))
        assertEquals(AnthropicProvider.ThinkingMode.Budget, AnthropicProvider.thinkingMode("claude-haiku-4-5-20251001"))
        assertEquals(AnthropicProvider.ThinkingMode.Budget, AnthropicProvider.thinkingMode("claude-sonnet-4-5"))
        assertEquals(AnthropicProvider.ThinkingMode.None, AnthropicProvider.thinkingMode("claude-3-haiku-20240307"))
    }
}
