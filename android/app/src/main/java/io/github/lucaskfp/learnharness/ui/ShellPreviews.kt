package io.github.lucaskfp.learnharness.ui

import androidx.compose.runtime.Composable
import io.github.lucaskfp.learnharness.ui.destinations.DestinationScreen
import io.github.lucaskfp.learnharness.ui.navigation.TopLevelDestination
import io.github.lucaskfp.learnharness.ui.preview.LhPreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

@LhPreviews
@Composable
private fun ShellPreview() = LearnHarnessApp()

@LhPreviews
@Composable
private fun GraphPreview() = LearnHarnessTheme {
    DestinationScreen(TopLevelDestination.Graph)
}

@LhPreviews
@Composable
private fun SettingsPreview() = LearnHarnessTheme {
    DestinationScreen(TopLevelDestination.Settings)
}
