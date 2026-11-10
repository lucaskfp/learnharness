package io.github.lucaskfp.learnharness.ui.teaching

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanLayoutTest {

    @Test
    fun `a chain puts one node per layer in dependency order`() {
        val layout = layoutPlan(
            nodeIds = listOf("c", "a", "b"),
            edges = listOf(PlanEdge("a", "b"), PlanEdge("b", "c")),
        )

        assertEquals(PlanCell(layer = 0, slot = 0), layout["a"])
        assertEquals(PlanCell(layer = 1, slot = 0), layout["b"])
        assertEquals(PlanCell(layer = 2, slot = 0), layout["c"])
    }

    @Test
    fun `nodes in the same layer take slots in authoring order`() {
        val layout = layoutPlan(
            nodeIds = listOf("modular", "oneWay", "primes", "goal"),
            edges = listOf(PlanEdge("modular", "goal"), PlanEdge("oneWay", "goal"), PlanEdge("primes", "goal")),
        )

        assertEquals(PlanCell(layer = 0, slot = 0), layout["modular"])
        assertEquals(PlanCell(layer = 0, slot = 1), layout["oneWay"])
        assertEquals(PlanCell(layer = 0, slot = 2), layout["primes"])
        assertEquals(PlanCell(layer = 1, slot = 0), layout["goal"])
    }

    @Test
    fun `a node waits for its deepest prerequisite`() {
        // a → b → d and a → d: d sits after b, not beside it.
        val layout = layoutPlan(
            nodeIds = listOf("a", "b", "d"),
            edges = listOf(PlanEdge("a", "b"), PlanEdge("b", "d"), PlanEdge("a", "d")),
        )

        assertEquals(2, layout.getValue("d").layer)
    }
}
