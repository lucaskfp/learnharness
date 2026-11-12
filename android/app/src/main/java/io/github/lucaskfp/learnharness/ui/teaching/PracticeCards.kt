package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.RichMarkdown
import io.github.lucaskfp.learnharness.ui.theme.amberColor
import io.github.lucaskfp.learnharness.ui.theme.correctColor

// The Practice Session's transcript cards, as in the mockups (6 · Prática).

/** A posed problem: the statement under a "Problem" eyebrow. */
@Composable
internal fun ProblemCard(statement: String, modifier: Modifier = Modifier) {
    PracticeCard(MaterialTheme.colorScheme.surfaceContainer, modifier) {
        Eyebrow(stringResource(R.string.problem))
        RichMarkdown(statement)
    }
}

/** The learner's thinking: an eyebrow, then a quote rail with muted italics. */
@Composable
internal fun ThinkingCard(text: String, modifier: Modifier = Modifier) {
    PracticeCard(MaterialTheme.colorScheme.secondaryContainer, modifier, gap = 6) {
        Eyebrow(stringResource(R.string.your_thinking))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/** A concluded problem: the outcome chip and the verdict, then the revealed model solution. */
@Composable
internal fun ConcludedCard(item: TranscriptItem.ConcludedCard, modifier: Modifier = Modifier) {
    PracticeCard(MaterialTheme.colorScheme.surfaceContainer, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow(stringResource(R.string.concluded))
            OutcomeChip(item.outcome)
        }
        if (item.verdict.isNotBlank()) {
            Text(item.verdict, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.modelSolution?.let { solution ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Eyebrow(stringResource(R.string.model_solution))
            RichMarkdown(solution)
        }
    }
}

/** The outcome as a chip: green solved, amber with hints, error surrendered, neutral otherwise. */
@Composable
internal fun OutcomeChip(outcome: ProblemOutcome, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (content, container) = when (outcome) {
        ProblemOutcome.Solved -> correctColor.let { it to it.copy(alpha = 0.15f) }
        ProblemOutcome.SolvedWithHints -> amberColor.let { it to it.copy(alpha = 0.15f) }
        ProblemOutcome.Surrendered -> colors.error.let { it to it.copy(alpha = 0.15f) }
        ProblemOutcome.MovedOn, ProblemOutcome.Dismissed -> colors.onSurfaceVariant to colors.surfaceContainerHighest
    }
    Text(
        stringResource(outcome.label),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PracticeCard(
    container: Color,
    modifier: Modifier = Modifier,
    gap: Int = 8,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
    ) { content() }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
