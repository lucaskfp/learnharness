package io.github.lucaskfp.learnharness.ui.teaching

/** A dependency in the Plan: [from] must be understood before [to]. */
data class PlanEdge(val from: String, val to: String)

/** Where a Plan node sits: its [layer] (top to bottom) and its [slot] within that layer (left to right). */
data class PlanCell(val layer: Int, val slot: Int)

/**
 * Places a Plan's nodes in layers: a node's layer is the length of the longest dependency
 * path leading to it, so every edge points to a later layer.
 */
fun layoutPlan(nodeIds: List<String>, edges: List<PlanEdge>): Map<String, PlanCell> {
    val depth = mutableMapOf<String, Int>()
    fun depthOf(id: String): Int = depth.getOrPut(id) {
        edges.filter { it.to == id }.maxOfOrNull { depthOf(it.from) + 1 } ?: 0
    }
    val taken = mutableMapOf<Int, Int>()
    return nodeIds.associateWith { id ->
        val layer = depthOf(id)
        val slot = taken.getOrDefault(layer, 0)
        taken[layer] = slot + 1
        PlanCell(layer, slot)
    }
}
