package io.github.lucaskfp.learnharness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The only hues besides error, by the owner's decision: the desktop's confidence ramp
// (components/confidence-rail.tsx) and its green for a correct answer.

/** Confidence ramp, one color per stop of 0, 30, 50, 70, 90 and 100: red to emerald. */
val ConfidenceRamp = listOf(
    Color(0xFFEF4444),
    Color(0xFFF97316),
    Color(0xFFEAB308),
    Color(0xFF84CC16),
    Color(0xFF22C55E),
    Color(0xFF10B981),
)

/** Correct answers: the ramp's emerald, darkened on the light theme for contrast. */
val correctColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF10B981) else Color(0xFF047857)
