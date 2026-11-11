package io.github.lucaskfp.learnharness.engine.plan

import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The Plan's pure folds: the mermaid subset, the DAG rules and the node-derived position. */
class PlanTest {
    private fun parsed(mermaid: String): PlanParse.Ok = parseMermaidPlan(mermaid) as? PlanParse.Ok
        ?: fail("expected the plan to parse: ${(parseMermaidPlan(mermaid) as PlanParse.Problems).problems}") as Nothing

    private fun problems(mermaid: String): List<String> = (parseMermaidPlan(mermaid) as PlanParse.Problems).problems

    @Test
    fun aChainExpandsToRequiresEdgesWithLabelsAsTitles() {
        val plan = parsed("graph TD\n  slope[The slope idea] --> rule[The power rule] --> chain(\"Chain rule\")")

        assertEquals(
            listOf(PlanNode("slope", "The slope idea"), PlanNode("rule", "The power rule"), PlanNode("chain", "Chain rule")),
            plan.nodes,
        )
        assertEquals(listOf(PlanEdge("slope", "rule"), PlanEdge("rule", "chain")), plan.edges)
    }

    @Test
    fun aBareNodeTakesItsIdAsTitleAndFirstLabelWins() {
        val plan = parsed("flowchart LR\n%% a comment\n  alone\n  a[First] --> b\n  a[Second] --> b")

        assertEquals(listOf("alone", "a", "b").zip(listOf("alone", "First", "b")).map { PlanNode(it.first, it.second) }, plan.nodes)
        assertEquals(listOf(PlanEdge("a", "b")), plan.edges)
    }

    @Test
    fun everyProblemIsReportedWithItsLineNumber() {
        val found = problems("graph TD\n  Bad-Id --> ok\n  a --> a\n  ???")

        assertEquals(3, found.size)
        assertTrue(found[0].startsWith("line 2:") && "lowercase slug" in found[0])
        assertTrue(found[1].startsWith("line 3:") && "self-loop" in found[1])
        assertTrue(found[2].startsWith("line 4:"))
    }

    @Test
    fun aMissingHeaderAndAnEmptyPlanAreProblems() {
        assertTrue("expected the header" in problems("a --> b").first())
        assertEquals(1, problems("  \n").size)
    }

    private fun rejected(nodes: List<PlanNode>, edges: List<PlanEdge>): String = try {
        createTree(nodes, edges)
        fail("expected a PlanTreeError") as Nothing
    } catch (e: PlanTreeError) {
        e.message!!
    }

    private val a = PlanNode("a", "A")
    private val b = PlanNode("b", "B")
    private val c = PlanNode("c", "C")

    @Test
    fun aValidDagIsBuiltAsVersionOne() {
        val tree = createTree(listOf(a, b, c), listOf(PlanEdge("a", "b"), PlanEdge("a", "c"), PlanEdge("b", "c")))

        assertEquals(1, tree.version)
        assertEquals(listOf("a", "b", "c"), tree.nodes.map { it.id })
    }

    @Test
    fun aCycleIsRejectedNamingTheEdgeThatClosesIt() {
        val message = rejected(listOf(a, b, c), listOf(PlanEdge("a", "b"), PlanEdge("b", "c"), PlanEdge("c", "a")))

        assertTrue(message, "\"c\" → \"a\" would create a cycle" in message)
    }

    @Test
    fun selfLoopsDuplicateEdgesDuplicateIdsAndUnknownEndpointsAreRejected() {
        assertTrue("self-loop" in rejected(listOf(a), listOf(PlanEdge("a", "a"))))
        assertTrue("already exists" in rejected(listOf(a, b), listOf(PlanEdge("a", "b"), PlanEdge("a", "b"))))
        assertTrue("Duplicate node id \"a\"" in rejected(listOf(a, a), emptyList()))
        assertTrue("unknown node id \"zzz\"" in rejected(listOf(a), listOf(PlanEdge("a", "zzz"))))
    }

    private val tree = createTree(listOf(a, b, c), listOf(PlanEdge("a", "b"), PlanEdge("b", "c")))

    private fun quizOn(nodeId: String?, tool: String = "quiz", id: String = "t$nodeId") = AssistantMessage(
        id = "m_$id",
        content = listOf(ToolCall(id, tool, buildJsonObject { nodeId?.let { put("nodeId", it) } })),
        model = "m",
        stopReason = StopReason.ToolUse,
        timestamp = 1,
    )

    @Test
    fun positionComesFromTaggedCallsAndNothingBeforeTheFirstOne() {
        assertEquals(PlanPosition(null, emptySet()), derivePlanPosition(tree, listOf(UserMessage("u", "hi", 1))))

        val position = derivePlanPosition(tree, listOf(quizOn("a"), quizOn("b", id = "t2")))

        assertEquals(PlanPosition("b", setOf("a")), position)
    }

    @Test
    fun severalChecksOnOneNodeAreOneStayAndExplainTagsToo() {
        val position = derivePlanPosition(tree, listOf(quizOn("a"), quizOn("a", id = "t2"), quizOn("b", "explain", id = "t3")))

        assertEquals(PlanPosition("b", setOf("a")), position)
    }

    @Test
    fun aRevisitMakesTheNodeBothVisitedAndCurrent() {
        val position = derivePlanPosition(tree, listOf(quizOn("a"), quizOn("b", id = "t2"), quizOn("a", id = "t3")))

        assertEquals(PlanPosition("a", setOf("a", "b")), position)
    }

    @Test
    fun untaggedAndUnknownNodeIdsMoveNothing() {
        val position = derivePlanPosition(tree, listOf(quizOn("a"), quizOn(null, id = "t2"), quizOn("gone", id = "t3")))

        assertEquals(PlanPosition("a", emptySet()), position)
        assertNull(derivePlanPosition(tree, emptyList()).current)
    }
}
