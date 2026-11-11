package io.github.lucaskfp.learnharness.engine

import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.plan.PlanPosition
import io.github.lucaskfp.learnharness.engine.plan.PlanTree
import io.github.lucaskfp.learnharness.engine.plan.derivePlanPosition
import io.github.lucaskfp.learnharness.engine.plan.latestPlanTree
import io.github.lucaskfp.learnharness.engine.question.QuestionTools
import io.github.lucaskfp.learnharness.engine.tools.AgentKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ids of the successful `probe_done` results, in order (upstream `probeDoneResultIds`). A call from
 * a failed or aborted generation grants nothing; its result must match both call id and tool name,
 * must not be an error and must not refuse with `details.ok == false`. Bare or refused calls leave
 * the Prober in charge.
 */
internal fun probeDoneResultIds(messages: List<LhMessage>): Set<String> {
    val calls = mutableSetOf<String>()
    val results = mutableSetOf<String>()
    for (message in messages) {
        if (message is AssistantMessage) {
            if (message.stopReason == StopReason.Error || message.stopReason == StopReason.Aborted) continue
            message.toolCalls.filter { it.name == "probe_done" }.forEach { calls += it.id }
        } else if (message is ToolResultMessage && message.toolName == "probe_done" && message.toolCallId in calls && !message.isError) {
            val ok = (message.details as? JsonObject)?.get("ok")?.jsonPrimitive?.booleanOrNull
            if (ok != false) results += message.id
        }
    }
    return results
}

internal fun hasProbeDone(messages: List<LhMessage>): Boolean = probeDoneResultIds(messages).isNotEmpty()

/** Which agent a request is built for: the Prober until the probe has concluded, then the Teacher. */
internal fun agentFor(messages: List<LhMessage>): AgentKind = if (hasProbeDone(messages)) AgentKind.Teacher else AgentKind.Prober

/**
 * Is a question still waiting in the newest assistant message? `probe_done` is refused while one
 * is open (upstream `hasUnresolvedQuestion`).
 */
internal fun hasUnresolvedQuestion(messages: List<LhMessage>): Boolean {
    val newest = messages.lastOrNull { it is AssistantMessage } as? AssistantMessage ?: return false
    val answered = messages.filterIsInstance<ToolResultMessage>().map { it.toolCallId }.toSet()
    return newest.toolCalls.any { it.name in QuestionTools && it.id !in answered }
}

/**
 * Index of the assistant message that first asks a question tagged with a node of the Plan then in
 * force: the seam where Planning becomes Teaching (upstream `phaseAt3Lh`). A call naming an unknown
 * node was rejected and starts nothing.
 */
internal fun teachingStartIndex(messages: List<LhMessage>): Int? {
    var tree: PlanTree? = null
    messages.forEachIndexed { index, message ->
        if (message is ToolResultMessage && message.toolName == "plan_init") {
            latestPlanTree(messages.subList(0, index + 1))?.let { tree = it }
        } else if (message is AssistantMessage) {
            val ids = tree?.nodes?.map { it.id }.orEmpty()
            val tagged = message.toolCalls.any { call ->
                (call.name == "quiz" || call.name == "explain") &&
                    call.arguments["nodeId"]?.jsonPrimitive?.contentOrNull in ids
            }
            if (tagged) return index
        }
    }
    return null
}

internal fun phaseOf(messages: List<LhMessage>): SessionPhase = when {
    !hasProbeDone(messages) -> SessionPhase.Probing
    teachingStartIndex(messages) != null -> SessionPhase.Teaching
    else -> SessionPhase.Planning
}

/** The Plan the learner sees: the latest tree, with the position derived from the tagged calls. */
internal fun planViewOf(messages: List<LhMessage>): PlanView? {
    val tree = latestPlanTree(messages) ?: return null
    return planView(tree, derivePlanPosition(tree, messages))
}

internal fun planView(tree: PlanTree, position: PlanPosition) =
    PlanView(tree.nodes, tree.edges, position.current, position.visited)
