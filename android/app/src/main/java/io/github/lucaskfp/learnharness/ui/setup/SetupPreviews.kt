package io.github.lucaskfp.learnharness.ui.setup

import androidx.compose.runtime.Composable
import io.github.lucaskfp.learnharness.ui.preview.FakeSetup
import io.github.lucaskfp.learnharness.ui.preview.LhLargeFontPreview
import io.github.lucaskfp.learnharness.ui.preview.LhPhonePreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun SetupEmptyPreview() = LearnHarnessTheme { SetupScreen(FakeSetup.empty()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun SetupKeyTestingPreview() = LearnHarnessTheme { SetupScreen(FakeSetup.keyTesting()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun SetupKeyInvalidPreview() = LearnHarnessTheme { SetupScreen(FakeSetup.keyInvalid()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun SetupKeyValidPreview() = LearnHarnessTheme { SetupScreen(FakeSetup.keyValid()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun SetupCustomEndpointPreview() = LearnHarnessTheme { SetupScreen(FakeSetup.customEndpoint()) }
