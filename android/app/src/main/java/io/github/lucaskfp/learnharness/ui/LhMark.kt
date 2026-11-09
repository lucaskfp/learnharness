package io.github.lucaskfp.learnharness.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R

/**
 * The LearnHarness mark (build/icon-flat.svg), drawn in theme colors so it reads on light and
 * dark surfaces: an orbit arc with a hollow and a solid circle. Geometry is in the SVG's
 * 1024-unit space, cropped to the mark's 806-unit extent.
 */
@Composable
fun LhMark(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.app_name)
    val orbit = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(MarkSize).semantics { contentDescription = description }) {
        val unit = this.size.minDimension / EXTENT
        fun at(x: Float, y: Float) = Offset((x - ORIGIN) * unit, (y - ORIGIN) * unit)

        val orbitRadius = 307.67f * unit
        drawArc(
            color = orbit,
            startAngle = -12f,
            sweepAngle = 294f,
            useCenter = false,
            topLeft = at(512f, 512f) - Offset(orbitRadius, orbitRadius),
            size = Size(orbitRadius * 2, orbitRadius * 2),
            style = Stroke(width = 46.31f * unit),
        )
        // Hollow circle: outer radius 188.11, inner 142.28.
        drawCircle(
            color = ink,
            radius = 165.2f * unit,
            center = at(726.89f, 297.11f),
            style = Stroke(width = 45.83f * unit),
        )
        drawCircle(color = ink, radius = 188.11f * unit, center = at(297.11f, 726.89f))
    }
}

private val MarkSize = 40.dp
private const val ORIGIN = 109f
private const val EXTENT = 806f
