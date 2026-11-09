package io.github.lucaskfp.learnharness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * LearnHarness theme: Material 3 Expressive with the monochrome brand palette,
 * the expressive motion scheme and the stock M3 type scale. No dynamic color.
 */
@Composable
fun LearnHarnessTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) LhDarkColors else LhLightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
