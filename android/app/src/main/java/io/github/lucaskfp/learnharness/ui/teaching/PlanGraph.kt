package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R

private enum class NodeStatus { Visited, Current, Upcoming }

/**
 * The Plan drawn as a top-to-bottom dependency graph: each node waits below its prerequisites
 * ([layoutPlan]). The current node is filled, visited nodes carry a check.
 */
@Composable
fun PlanGraph(plan: Plan, modifier: Modifier = Modifier) {
    val cells = remember(plan) { layoutPlan(plan.nodes.map { it.id }, plan.edges) }
    // Node boxes, written by the layout pass and read by the edge drawing (draw runs after layout).
    val bounds = remember(plan) { arrayOfNulls<IntRect>(plan.nodes.size) }
    val index = remember(plan) { plan.nodes.withIndex().associate { (i, node) -> node.id to i } }
    val edgeColor = MaterialTheme.colorScheme.outline
    val currentDescription = stringResource(R.string.plan_node_current)
    val visitedDescription = stringResource(R.string.plan_node_visited)

    Layout(
        modifier = modifier.drawBehind {
            plan.edges.forEach { edge ->
                val from = bounds[index.getValue(edge.from)] ?: return@forEach
                val to = bounds[index.getValue(edge.to)] ?: return@forEach
                val start = Offset(from.center.x.toFloat(), from.bottom.toFloat())
                val end = Offset(to.center.x.toFloat(), to.top.toFloat())
                val midY = (start.y + end.y) / 2
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                }
                drawPath(path, edgeColor, style = Stroke(width = 1.5.dp.toPx()))
            }
        },
        content = {
            plan.nodes.forEach { node ->
                val status = when {
                    node.id == plan.current -> NodeStatus.Current
                    node.id in plan.visited -> NodeStatus.Visited
                    else -> NodeStatus.Upcoming
                }
                PlanNodeBox(
                    title = node.title,
                    status = status,
                    modifier = Modifier.semantics {
                        when (status) {
                            NodeStatus.Current -> stateDescription = currentDescription
                            NodeStatus.Visited -> stateDescription = visitedDescription
                            NodeStatus.Upcoming -> Unit
                        }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val rowGap = 32.dp.roundToPx()
        val columnGap = 12.dp.roundToPx()
        val layers = cells.values.maxOf { it.layer } + 1
        val widest = plan.nodes.groupBy { cells.getValue(it.id).layer }.values.maxOf { it.size }
        val slotWidth = ((constraints.maxWidth - columnGap * (widest - 1)) / widest).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = slotWidth, minHeight = 0)) }
        val rowHeights = IntArray(layers)
        plan.nodes.forEachIndexed { i, node ->
            val layer = cells.getValue(node.id).layer
            rowHeights[layer] = maxOf(rowHeights[layer], placeables[i].height)
        }
        val rowTops = IntArray(layers)
        for (layer in 1 until layers) rowTops[layer] = rowTops[layer - 1] + rowHeights[layer - 1] + rowGap
        val height = rowTops.last() + rowHeights.last()
        val perLayer = plan.nodes.groupBy { cells.getValue(it.id).layer }.mapValues { it.value.size }

        layout(constraints.maxWidth, height) {
            plan.nodes.forEachIndexed { i, node ->
                val cell = cells.getValue(node.id)
                val inLayer = perLayer.getValue(cell.layer)
                val rowWidth = slotWidth * inLayer + columnGap * (inLayer - 1)
                val slotLeft = (constraints.maxWidth - rowWidth) / 2 + cell.slot * (slotWidth + columnGap)
                val placeable = placeables[i]
                val x = slotLeft + (slotWidth - placeable.width) / 2
                val y = rowTops[cell.layer] + (rowHeights[cell.layer] - placeable.height) / 2
                bounds[i] = IntRect(x, y, x + placeable.width, y + placeable.height)
                placeable.place(x, y)
            }
        }
    }
}

@Composable
private fun PlanNodeBox(title: String, status: NodeStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when (status) {
            NodeStatus.Current -> colors.primary
            NodeStatus.Visited -> colors.secondaryContainer
            NodeStatus.Upcoming -> colors.surface
        },
        contentColor = when (status) {
            NodeStatus.Current -> colors.onPrimary
            NodeStatus.Visited -> colors.onSecondaryContainer
            NodeStatus.Upcoming -> colors.onSurfaceVariant
        },
        border = if (status == NodeStatus.Upcoming) BorderStroke(1.dp, colors.outline) else null,
        modifier = modifier.widthIn(min = 72.dp, max = 180.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (status == NodeStatus.Visited) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (status == NodeStatus.Current) FontWeight.Bold else null,
                textAlign = TextAlign.Center,
            )
        }
    }
}
