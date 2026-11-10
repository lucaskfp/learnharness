@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.theme.ConfidenceRamp

/** How sure the learner is of an answer: a slider over [ConfidenceStops], colored by the ramp. */
@Composable
fun ConfidenceSlider(index: Int, onIndexChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val color = ConfidenceRamp[index]
    val percent = stringResource(R.string.confidence_percent, ConfidenceStops[index])
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.confidence), style = MaterialTheme.typography.labelLarge)
            Text(percent, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onIndexChange(it.toInt()) },
            valueRange = 0f..(ConfidenceStops.size - 1).toFloat(),
            steps = ConfidenceStops.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                activeTickColor = MaterialTheme.colorScheme.surface,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.semantics { stateDescription = percent },
        )
    }
}

/** The answered card's record of a confidence: the slider's rail, without a thumb. */
@Composable
fun ConfidenceRail(confidence: Int, modifier: Modifier = Modifier) {
    val index = ConfidenceStops.indexOf(confidence).takeIf { it >= 0 } ?: 2
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(confidence / 100f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(ConfidenceRamp[index]),
            )
        }
        Text(
            stringResource(R.string.confidence_percent, confidence),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
