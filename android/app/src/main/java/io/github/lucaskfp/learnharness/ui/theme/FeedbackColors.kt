package io.github.lucaskfp.learnharness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The only hues besides error, by the owner's decision: the desktop's confidence ramp
// (components/confidence-rail.tsx) and its green for a correct answer.

/** Confidence ramp, keyed by confidence stop in percent: red to emerald. */
private val ConfidenceRamp = mapOf(
    0 to Color(0xFFEF4444),
    30 to Color(0xFFF97316),
    50 to Color(0xFFEAB308),
    70 to Color(0xFF84CC16),
    90 to Color(0xFF22C55E),
    100 to Color(0xFF10B981),
)

/** The ramp color of a confidence stop (0, 30, 50, 70, 90 or 100 percent). */
fun confidenceColor(percent: Int): Color = ConfidenceRamp.getValue(percent)

/** Correct answers: the ramp's emerald, darkened on the light theme for contrast. */
val correctColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF10B981) else Color(0xFF047857)
