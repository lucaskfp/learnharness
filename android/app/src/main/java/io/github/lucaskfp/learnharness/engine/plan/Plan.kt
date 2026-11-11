package io.github.lucaskfp.learnharness.engine.plan

import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * The Plan: the Teacher's lesson map, a validated DAG written once with `plan_init` (and replaced
 * wholesale by calling it again). Ports upstream's `plan-tree.ts`, `mermaid-plan.ts` and
 * `derivePlanStatusesLh`. The tree carries structure only: where the lesson stands is derived from
 * the Teacher's node-tagged calls, never stored and never read from grades.
 */

@Serializable
data class PlanNode(val id: String, val title: String)

/** [from] must be understood before [to]. */
@Serializable
data class PlanEdge(val from: String, val to: String)

@Serializable
data class PlanTree(val nodes: List<PlanNode>, val edges: List<PlanEdge>, val version: Int = 1)

/** Thrown for an invalid Plan; the message is written for the agent, which fixes the call and retries. */
class PlanTreeError(message: String) : IllegalArgumentException(message)

/** Builds a tree (version 1), enforcing unique ids, known endpoints, no self-loops, duplicates or cycles. */
fun createTree(nodes: List<PlanNode>, edges: List<PlanEdge>): PlanTree {
    val ids = mutableSetOf<String>()
    for (node in nodes) {
        if (node.id.isBlank()) throw PlanTreeError("Every node needs a non-empty `id` (a stable slug like 'chain-rule-intuition').")
        if (node.title.isBlank()) throw PlanTreeError("Node \"${node.id}\" needs a non-empty `title`.")
        if (!ids.add(node.id)) throw PlanTreeError("Duplicate node id \"${node.id}\" — node ids must be unique.")
    }
    for (edge in edges) {
        if (edge.from !in ids) throw PlanTreeError("Edge `from` references unknown node id \"${edge.from}\" — it is not in the tree.")
        if (edge.to !in ids) throw PlanTreeError("Edge `to` references unknown node id \"${edge.to}\" — it is not in the tree.")
    }
    val accepted = mutableListOf<PlanEdge>()
    for (edge in edges) {
        // Each edge is checked against the ones already accepted, so a violation names its culprit.
        when {
            edge.from == edge.to -> throw PlanTreeError("Edge \"${edge.from}\" → \"${edge.to}\" is a self-loop — a node can't require itself.")
            edge in accepted -> throw PlanTreeError("Edge \"${edge.from}\" → \"${edge.to}\" already exists — duplicate edges are not allowed.")
            reaches(accepted, edge.to, edge.from) -> throw PlanTreeError(
                "Edge \"${edge.from}\" → \"${edge.to}\" would create a cycle: \"${edge.to}\" already leads (directly or transitively) " +
                    "back to \"${edge.from}\". \"from\" must be understood before \"to\" — the tree is a DAG.",
            )
        }
        accepted += edge
    }
    return PlanTree(nodes, accepted, version = 1)
}

/** Can [target] be reached from [start] along [edges]? */
private fun reaches(edges: List<PlanEdge>, start: String, target: String): Boolean {
    val next = edges.groupBy({ it.from }, { it.to })
    val stack = ArrayDeque(listOf(start))
    val seen = mutableSetOf<String>()
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        if (current == target) return true
        if (!seen.add(current)) continue
        stack.addAll(next[current].orEmpty())
    }
    return false
}

/** The outcome of reading a mermaid block: a plan, or every problem found (never just the first). */
sealed interface PlanParse {
    data class Ok(val nodes: List<PlanNode>, val edges: List<PlanEdge>) : PlanParse
    data class Problems(val problems: List<String>) : PlanParse
}

private val HeaderPattern = Regex("^(graph|flowchart)\\s+(td|tb|lr|rl|bt)\\s*$", RegexOption.IGNORE_CASE)
private val SlugPattern = Regex("^[a-z0-9]+([_-][a-z0-9]+)*$")

/** Any arrow spelling is a requires-edge; chains split on it. */
private val ArrowPattern = Regex("-->|==>|-\\.->")

/** One edge endpoint: `id`, `id[title]`, `id("title")` or `id(title)`. */
private val EndpointPattern = Regex("^([^\\s()\\[\\]]+)\\s*(?:\\[([^\\[\\]]*)]|\\(\\s*\"([^\"]*)\"\\s*\\)|\\(([^()]*)\\))?$")

private data class Endpoint(val id: String, val label: String)

private fun parseEndpoint(text: String): Endpoint? {
    val match = EndpointPattern.matchEntire(text.trim()) ?: return null
    var label = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }.trim()
    val quoted = label.length >= 2 &&
        ((label.startsWith("\"") && label.endsWith("\"")) || (label.startsWith("'") && label.endsWith("'")))
    if (quoted) label = label.substring(1, label.length - 1).trim()
    return Endpoint(match.groupValues[1], label)
}

/**
 * Reads the mermaid subset the Teacher writes: a `graph TD` header, `slug[Title]` nodes and
 * `a --> b` requires-edges (chains fine). The one strict rule is that ids are permanent lowercase
 * slugs, since every quiz references them. Ports upstream's `parseMermaidPlan`.
 */
fun parseMermaidPlan(mermaid: String): PlanParse {
    val problems = mutableListOf<String>()
    val lines = mermaid.split(Regex("\r?\n"))

    val first = lines.indexOfFirst { it.isNotBlank() && !it.trim().startsWith("%%") }
    if (first == -1) {
        return PlanParse.Problems(listOf("The plan is empty — expected a `graph TD` block with nodes and `-->` requires-edges."))
    }
    val header = lines[first].trim()
    if (!HeaderPattern.matches(header)) {
        problems += "line ${first + 1}: expected the header `graph TD` (or flowchart TD/TB) first, got `$header`."
    }

    val titles = LinkedHashMap<String, String>() // id → title, the first label wins
    val edges = mutableListOf<PlanEdge>()

    fun register(endpoint: Endpoint, lineNo: Int) {
        if (!SlugPattern.matches(endpoint.id)) {
            problems += "line $lineNo: node id `${endpoint.id}` must be a lowercase slug (a-z, 0-9, joined by hyphens or underscores — " +
                "e.g. `chain-rule-intuition`); ids are permanent and checks reference them."
            return
        }
        if (endpoint.id !in titles) titles[endpoint.id] = endpoint.label.ifEmpty { endpoint.id }
        else if (endpoint.label.isNotEmpty() && titles[endpoint.id] == endpoint.id) titles[endpoint.id] = endpoint.label
    }

    for (i in first + 1 until lines.size) {
        val line = lines[i].trim()
        val no = i + 1
        if (line.isEmpty() || line.startsWith("%%")) continue

        val segments = line.split(ArrowPattern)
        if (segments.size >= 2) {
            val endpoints = mutableListOf<Endpoint>()
            var unreadable = false
            for (segment in segments) {
                val endpoint = parseEndpoint(segment)
                if (endpoint == null) {
                    problems += "line $no: can't read the edge `$line` — expected `a[title] --> b[title]` (chains like `a --> b --> c` are fine)."
                    unreadable = true
                    break
                }
                endpoints += endpoint
            }
            if (unreadable) continue
            endpoints.forEach { register(it, no) }
            for (k in 0 until endpoints.size - 1) {
                val from = endpoints[k]
                val to = endpoints[k + 1]
                if (from.id == to.id) {
                    problems += "line $no: self-loop `${from.id} --> ${to.id}` — a node can't require itself."
                    continue
                }
                val edge = PlanEdge(from.id, to.id)
                if (edge !in edges) edges += edge
            }
        } else {
            val node = parseEndpoint(line)
            if (node == null) {
                problems += "line $no: can't parse `$line` — ids are the strict part (lowercase slugs: a-z, 0-9, hyphens, underscores); " +
                    "node lines (`slug[title]`) and requires-edges (`a --> b`) are the accepted forms; subgraphs, styles, and classes are not part of the subset."
                continue
            }
            register(node, no)
        }
    }

    if (problems.isNotEmpty()) return PlanParse.Problems(problems)
    if (titles.isEmpty()) return PlanParse.Problems(listOf("No nodes found — the plan needs at least one."))
    return PlanParse.Ok(titles.map { (id, title) -> PlanNode(id, title) }, edges)
}

/** Where the lesson stands on the Plan: derived from tagged calls, never stored. */
data class PlanPosition(val current: String?, val visited: Set<String>)

/**
 * Position from the Teacher's `quiz` and `explain` CALLS (any state; results and grades feed
 * nothing): the current node is the one the most recent tagged call is about, visited is every
 * node of an earlier stay. Untagged calls and ids missing from [tree] (a rewrite may have dropped
 * them) move nothing, and before the first tagged call nothing is current. Upstream's
 * `derivePlanStatusesLh`.
 */
fun derivePlanPosition(tree: PlanTree, messages: List<LhMessage>): PlanPosition {
    val ids = tree.nodes.map { it.id }.toSet()
    val stays = mutableListOf<String>() // consecutive duplicates collapsed: several checks on one node are one stay
    for (message in messages) {
        if (message !is AssistantMessage) continue
        for (call in message.toolCalls) {
            if (call.name != "quiz" && call.name != "explain") continue
            val nodeId = call.arguments["nodeId"]?.jsonPrimitive?.contentOrNull ?: continue
            if (nodeId !in ids) continue
            if (stays.lastOrNull() != nodeId) stays += nodeId
        }
    }
    return PlanPosition(stays.lastOrNull(), stays.dropLast(1).toSet())
}

/** The tree of the latest successful `plan_init` result: replacing the Plan is calling it again. */
fun latestPlanTree(messages: List<LhMessage>): PlanTree? =
    messages.filterIsInstance<ToolResultMessage>()
        .lastOrNull { it.toolName == "plan_init" && !it.isError && planOk(it) }
        ?.let(::planTreeOf)

private fun planOk(result: ToolResultMessage): Boolean =
    (result.details as? JsonObject)?.get("ok")?.jsonPrimitive?.booleanOrNull == true

private fun planTreeOf(result: ToolResultMessage): PlanTree? = try {
    (result.details as? JsonObject)?.get("tree")?.let { LhJson.decodeFromJsonElement(PlanTree.serializer(), it) }
} catch (_: Exception) {
    null
}
