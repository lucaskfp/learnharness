package io.github.lucaskfp.learnharness.ui.newsession

import androidx.compose.runtime.Composable
import io.github.lucaskfp.learnharness.ui.preview.FakeNewSession
import io.github.lucaskfp.learnharness.ui.preview.LhLargeFontPreview
import io.github.lucaskfp.learnharness.ui.preview.LhPhonePreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun NewSessionEmptyPreview() = LearnHarnessTheme { NewSessionScreen(FakeNewSession.emptyTeaching()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun NewSessionFilledPreview() = LearnHarnessTheme { NewSessionScreen(FakeNewSession.filledTeaching()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun NewSessionPracticePreview() = LearnHarnessTheme { NewSessionScreen(FakeNewSession.practice()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun NewSessionKindMenuPreview() = LearnHarnessTheme { NewSessionScreen(FakeNewSession.kindMenuOpen()) }
