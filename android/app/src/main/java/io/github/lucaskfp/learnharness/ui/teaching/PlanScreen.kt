@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R

private const val MinZoom = 0.5f
private const val MaxZoom = 4f

/** The live Plan in full screen, opened from the Session's top bar on phones. */
@Composable
fun PlanScreen(plan: Plan, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(pluralStringResource(R.plurals.plan_node_count, plan.nodes.size, plan.nodes.size)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        // Pinch to zoom, drag to pan; both survive the Plan changing under the learner.
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(MinZoom, MaxZoom)
                        // Keep the point under the fingers fixed while zooming.
                        offset = (offset - centroid) * (next / scale) + centroid + pan
                        scale = next
                    }
                },
        ) {
            PlanGraph(
                plan,
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}
