package io.github.lucaskfp.learnharness.ui.sessions

import androidx.compose.runtime.Composable
import io.github.lucaskfp.learnharness.ui.preview.FakeSessions
import io.github.lucaskfp.learnharness.ui.preview.LhLargeFontPreview
import io.github.lucaskfp.learnharness.ui.preview.LhPreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsEmptyPreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.empty()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsPopulatedPreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.populated()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsFabMenuPreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.fabMenuExpanded()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsSearchResultsPreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.searchResults()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsSearchNoMatchPreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.searchNoMatch()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun SessionsDeletePreview() = LearnHarnessTheme { SessionsScreen(FakeSessions.deleteConfirmation()) }
