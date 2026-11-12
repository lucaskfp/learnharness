package io.github.lucaskfp.learnharness.engine

import io.github.lucaskfp.learnharness.engine.tools.ToolNames
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
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import io.github.lucaskfp.learnharness.engine.plan.PlanPosition
import io.github.lucaskfp.learnharness.engine.plan.latestPlanTree
import io.github.lucaskfp.learnharness.engine.question.ArmedQuestion
import io.github.lucaskfp.learnharness.engine.question.QuestionAnswerData
import io.github.lucaskfp.learnharness.engine.question.QuestionTools
import io.github.lucaskfp.learnharness.engine.question.armQuestion

/** What the UI shows of a saved history: the transcript, and the question waiting, if any. */
internal data class Projection(val transcript: List<TranscriptEntry>, val pending: ArmedQuestion?)

/**
 * Derives the transcript and the pending question from the saved history (the history is the one
 * source of truth, so a Session resumes mid-question for free).
 *
 * A question is pending when the newest assistant message posed it, finished cleanly and nothing
 * has answered it. A question call that failed validation has an error result and is not shown.
 */
internal fun project(messages: List<LhMessage>): Projection {
    val results = messages.filterIsInstance<ToolResultMessage>().associateBy { it.toolCallId }
    val newestAssistant = messages.indexOfLast { it is AssistantMessage }
    val transcript = mutableListOf<TranscriptEntry>()
    var pending: ArmedQuestion? = null
    val teachingStart = teachingStartIndex(messages)
    var plansSeen = 0
    messages.forEachIndexed { index, message ->
        when (message) {
            is UserMessage -> transcript += TranscriptEntry.Learner(message.content)
            is MarkerMessage -> Unit
            is ToolResultMessage -> if (message.toolName == ToolNames.PlanInit) {
                // Each successful plan_init is a snapshot, so a replacement is visible; the first opens the Planning seam.
                val tree = latestPlanTree(listOf(message))
                if (tree != null) {
                    plansSeen++
                    if (plansSeen == 1) transcript += TranscriptEntry.PhaseBoundary(SessionPhase.Planning, tree.nodes.size)
                    transcript += TranscriptEntry.PlanSnapshot(planView(tree, PlanPosition(null, emptySet())))
                }
            }
            is AssistantMessage -> {
                if (index == teachingStart) transcript += TranscriptEntry.PhaseBoundary(SessionPhase.Teaching)
                if (message.stopReason == StopReason.Aborted) {
                    // An aborted generation's tool calls were never run: show its prose, then the cut.
                    message.content.forEach { block ->
                        if (block is TextContent && block.text.isNotBlank()) transcript += TranscriptEntry.AgentText(block.text)
                        if (block is ThinkingContent && block.redacted != true && block.thinking.isNotBlank()) {
                            transcript += TranscriptEntry.Reasoning(block.thinking)
                        }
                    }
                    transcript += TranscriptEntry.Interrupted
                    return@forEachIndexed
                }
                for (block in message.content) {
                    when (block) {
                        is TextContent -> if (block.text.isNotBlank()) transcript += TranscriptEntry.AgentText(block.text)
                        is ThinkingContent -> if (block.redacted != true && block.thinking.isNotBlank()) {
                            transcript += TranscriptEntry.Reasoning(block.thinking)
                        }
                        is ToolCall -> {
                            if (block.name !in QuestionTools) continue
                            val result = results[block.id]
                            if (result == null) {
                                val posed = index == newestAssistant && message.stopReason == StopReason.ToolUse
                                if (posed && pending == null) pending = armQuestion(block.id, block.name, block.arguments)
                            } else if (!result.isError) {
                                answerOf(result)?.let { transcript += TranscriptEntry.Answered(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    return Projection(transcript, pending)
}

private fun answerOf(result: ToolResultMessage): QuestionAnswerData? = try {
    result.details?.let { LhJson.decodeFromJsonElement(QuestionAnswerData.serializer(), it) }
} catch (_: Exception) {
    null
}

/**
 * Where the pending question [toolCallId] stands among the questions its message posed together:
 * its 1-based place and how many there are. A question call refused with an error is not counted.
 */
internal fun siblingPlace(messages: List<LhMessage>, toolCallId: String): Pair<Int, Int> {
    val asking = messages.lastOrNull { it is AssistantMessage && it.toolCalls.any { call -> call.id == toolCallId } } as? AssistantMessage
        ?: return 1 to 1
    val refused = messages.filterIsInstance<ToolResultMessage>().filter { it.isError }.map { it.toolCallId }.toSet()
    val siblings = asking.toolCalls.filter { it.name in QuestionTools && it.id !in refused }.map { it.id }
    return (siblings.indexOf(toolCallId) + 1).coerceAtLeast(1) to siblings.size.coerceAtLeast(1)
}

/** The partial output of a Turn in flight, as transcript entries. */
internal fun liveEntries(blocks: List<ContentBlock>): List<TranscriptEntry> = blocks.mapNotNull { block ->
    when (block) {
        is TextContent -> block.text.takeIf { it.isNotBlank() }?.let(TranscriptEntry::AgentText)
        is ThinkingContent -> block.thinking.takeIf { it.isNotBlank() && block.redacted != true }
            ?.let(TranscriptEntry::Reasoning)
        is ToolCall -> null
    }
}

/**
 * The history as a provider should read it (upstream `toLlmMessages`): failed generations are
 * dropped, and a question call nobody answered gets the interrupted result the protocol demands.
 */
internal fun toLlmMessages(messages: List<LhMessage>, interruptedText: String, now: Long): List<LhMessage> {
    val out = mutableListOf<LhMessage>()
    messages.forEachIndexed { index, message ->
        if (message is AssistantMessage && (message.stopReason == StopReason.Error || message.stopReason == StopReason.Aborted)) {
            return@forEachIndexed
        }
        // Markers flatten to user turns for the model, as upstream's `toLlmMessages` does.
        out += if (message is MarkerMessage) UserMessage(message.id, message.content, message.timestamp) else message
        if (message is AssistantMessage) {
            val answered = messages.drop(index + 1).filterIsInstance<ToolResultMessage>().map { it.toolCallId }.toSet()
            message.toolCalls.filter { it.id !in answered }.forEach { call ->
                out += ToolResultMessage(
                    id = "interrupted_${call.id}",
                    toolCallId = call.id,
                    toolName = call.name,
                    content = listOf(TextContent(interruptedText)),
                    isError = false,
                    timestamp = now,
                )
            }
        }
    }
    return out
}
