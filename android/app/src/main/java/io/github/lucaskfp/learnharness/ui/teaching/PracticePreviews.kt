package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.ui.preview.FakePractice
import io.github.lucaskfp.learnharness.ui.preview.LhLargeFontPreview
import io.github.lucaskfp.learnharness.ui.preview.LhPhonePreviews
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme

// The Practice Session, as in the mockups (6 · Prática).

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun PracticeActiveProblemPreview() = LearnHarnessTheme { TeachingSessionScreen(FakePractice.activeProblem()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun PracticeHintPreview() = LearnHarnessTheme { TeachingSessionScreen(FakePractice.hint()) }

@LhPhonePreviews
@LhLargeFontPreview
@Composable
private fun PracticeConcludedPreview() = LearnHarnessTheme { TeachingSessionScreen(FakePractice.concluded()) }

@LhPhonePreviews
@Composable
private fun PracticeOutcomesPreview() = LearnHarnessTheme { PracticeOutcomes() }

/** The outcomes the Practitioner can record, as heading-only cards. */
@Composable
internal fun PracticeOutcomes() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FakePractice.outcomes.forEach { ConcludedCard(TranscriptItem.ConcludedCard(it, verdict = "", modelSolution = null)) }
    }
}
