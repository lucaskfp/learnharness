package io.github.lucaskfp.learnharness.engine

import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.tools.ToolNames

/**
 * The Practice Session's folds over the saved history, ported from upstream
 * `src/core/native-messages.ts`. Calls count only through their results (upstream's "honest
 * activation"): a problem is posed when its result is clean, and a conclude frees the problem when
 * its result is clean. Refused and unanswered calls never happened.
 */

/** Ids of the tool calls whose results are clean (not errors). */
private fun cleanCallIds(messages: List<LhMessage>): Set<String> =
    messages.filterIsInstance<ToolResultMessage>().filter { !it.isError }.map { it.toolCallId }.toSet()

/** The newest clean `problem` call that no clean `conclude` follows (upstream `activeProblemOf`). */
internal fun activeProblemOf(messages: List<LhMessage>): ToolCall? {
    val clean = cleanCallIds(messages)
    for (message in messages.asReversed()) {
        if (message !is AssistantMessage) continue
        for (call in message.toolCalls.asReversed()) {
            if (call.id !in clean) continue
            if (call.name == ToolNames.Conclude) return null
            if (call.name == ToolNames.Problem) return call
        }
    }
    return null
}

/**
 * The `problem` call a `conclude` ends: the newest clean pose before it (upstream
 * `problemInputForConclude`). Its committed `modelSolution` is the reveal. Unlike upstream, a pose
 * later in the conclude's own message does not count: a conclude-then-pose message reveals the
 * concluded problem's solution, not the new one's.
 */
internal fun problemForConclude(messages: List<LhMessage>, concludeCallId: String): ToolCall? {
    val clean = cleanCallIds(messages)
    val at = messages.indexOfFirst { it is AssistantMessage && it.toolCalls.any { call -> call.id == concludeCallId } }
    if (at < 0) return null
    for (message in messages.subList(0, at + 1).asReversed()) {
        if (message !is AssistantMessage) continue
        val calls = message.toolCalls
        // In the conclude's own message, only the calls made before it count.
        val before = if (message === messages[at]) calls.subList(0, calls.indexOfFirst { it.id == concludeCallId }) else calls
        before.lastOrNull { it.name == ToolNames.Problem && it.id in clean }?.let { return it }
    }
    return null
}

/** Has any problem been posed? Next is the go-ahead between problems, never before the first. */
internal fun hasPosedProblem(messages: List<LhMessage>): Boolean {
    val clean = cleanCallIds(messages)
    return messages.any { it is AssistantMessage && it.toolCalls.any { call -> call.name == ToolNames.Problem && call.id in clean } }
}

/** Next is the pause's go-ahead: a Practice Session with nothing active, after a first problem. */
internal fun nextAllowed(kind: SessionKind?, messages: List<LhMessage>): Boolean =
    kind == SessionKind.Practice && activeProblemOf(messages) == null && hasPosedProblem(messages)

/** A Practice Session's phase: before the first problem, then whether a problem is open. */
internal fun practicePhaseOf(messages: List<LhMessage>): SessionPhase = when {
    activeProblemOf(messages) != null -> SessionPhase.ProblemOpen
    hasPosedProblem(messages) -> SessionPhase.ProblemConcluded
    else -> SessionPhase.Practice
}

/**
 * The Turn-scoped gate (upstream `PracticeGate`): seeded once from the saved history when the Turn
 * begins, then flipped by the Turn's own `problem` and `conclude` calls, so a conclude and the
 * next pose in one Turn see each other.
 */
class PracticeGate(var problemActive: Boolean)

