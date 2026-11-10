package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.runtime.Composable
import io.github.lucaskfp.learnharness.ui.preview.FakeTeaching
import io.github.lucaskfp.learnharness.ui.preview.LhLargeFontPreview
import io.github.lucaskfp.learnharness.ui.preview.LhPhonePreviews
import io.github.lucaskfp.learnharness.ui.preview.LhPreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

// Probe

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun ProbeMultipleChoicePreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.probeMultipleChoice()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun ProbeRecallPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.probeRecall()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun ProbeCollapsedPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.probeCollapsed()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun ProbeRunningPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.probeRunning()) }

// Plan

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun PlanInTranscriptPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.planInTranscript()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun PlanFullScreenPreview() = LearnHarnessTheme { PlanScreen(FakeTeaching.teachingPlan()) }

// Teach: phone and tablet (Plan beside the lesson)

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachPredictionPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachPrediction()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachExplanationPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachExplanation()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachQuizGradedPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachQuizGraded()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachImagesPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachImages()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachJobsPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachJobs()) }

@LhPreviews
@LhLargeFontPreview
@Composable
private fun TeachRunningPreview() = LearnHarnessTheme { TeachingSessionScreen(FakeTeaching.teachRunning()) }
