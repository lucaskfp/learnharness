@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.RichMarkdown
import io.github.lucaskfp.learnharness.ui.sessions.SessionPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One transcript entry. */
@Composable
fun TranscriptEntry(item: TranscriptItem, modifier: Modifier = Modifier, onOpenPlan: () -> Unit = {}) {
    when (item) {
        is TranscriptItem.LearnerMessage -> LearnerMessage(item.text, modifier)
        is TranscriptItem.AgentProse -> RichMarkdown(item.markdown, modifier)
        is TranscriptItem.Reasoning -> ReasoningBlock(item, modifier)
        is TranscriptItem.AnsweredQuestion -> AnsweredCard(item, modifier)
        is TranscriptItem.PhaseBoundary -> PhaseBoundary(item, modifier)
        is TranscriptItem.PlanSnapshot -> PlanBlock(item.plan, onOpenPlan, modifier)
        is TranscriptItem.ImageCard -> ImageCard(item, modifier)
        is TranscriptItem.JobCard -> JobCard(item, modifier)
    }
}

/** The learner's words: a full-width band with a rail, as on desktop (no chat sides). */
@Composable
private fun LearnerMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
    }
}

/** The learner's thinking: rail and muted italics, the desktop's inner-voice signature. */
@Composable
private fun LearnerThinking(text: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ReasoningBlock(item: TranscriptItem.Reasoning, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = {}, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
            Text(stringResource(R.string.reasoning), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = stringResource(if (item.expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(if (item.expanded) 180f else 0f),
            )
        }
        if (item.expanded) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun AnsweredCard(item: TranscriptItem.AnsweredQuestion, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(item.kind.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(item.text, style = MaterialTheme.typography.titleSmall)
            item.options.forEach { QuizOption(it, onClick = {}) }
            if (item.answer.isNotEmpty()) Text("“${item.answer}”", style = MaterialTheme.typography.bodyLarge)
            item.confidence?.let { ConfidenceRail(it, Modifier.fillMaxWidth()) }
            if (item.thinking.isNotEmpty()) LearnerThinking(item.thinking)
        }
    }
}

/** A quiet dotted rule where the Session enters a new phase. */
@Composable
private fun PhaseBoundary(item: TranscriptItem.PhaseBoundary, modifier: Modifier = Modifier) {
    val label = if (item.phase == SessionPhase.Planning && item.nodeCount != null) {
        pluralStringResource(R.plurals.plan_node_count, item.nodeCount, item.nodeCount)
    } else {
        stringResource(item.phase.label)
    }
    val color = MaterialTheme.colorScheme.outline
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DottedRule(color, Modifier.weight(1f))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        DottedRule(color, Modifier.weight(1f))
    }
}

@Composable
private fun DottedRule(color: Color, modifier: Modifier) {
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color,
            Offset(0f, 0f),
            Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun PlanBlock(plan: Plan, onOpenPlan: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanGraph(plan, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        OutlinedButton(onClick = onOpenPlan, modifier = Modifier.align(Alignment.End)) {
            Icon(painterResource(R.drawable.ic_open_in_full), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.open_plan), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/** The learner's photo, with the Teacher's labels pointing at parts of it. */
@Composable
private fun ImageCard(item: TranscriptItem.ImageCard, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val accent = MaterialTheme.colorScheme.error
    val description = item.annotations.joinToString { it.label }
    // A photo at full tablet width would push its caption off screen.
    Card(modifier.widthIn(max = 560.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .semantics { contentDescription = description },
        ) {
            drawWhiteboardPhoto()
            item.annotations.forEach { annotation ->
                val target = Offset(size.width * annotation.x, size.height * annotation.y)
                val text = measurer.measure(annotation.label, labelStyle.copy(color = Color.White), maxLines = 1)
                val labelTopLeft = Offset(
                    (target.x - text.size.width / 2f).coerceIn(8f, size.width - text.size.width - 24f),
                    if (target.y > size.height / 2) target.y - 72.dp.toPx() else target.y + 40.dp.toPx(),
                )
                val padding = 8.dp.toPx()
                val labelCenter = Offset(labelTopLeft.x + text.size.width / 2f, labelTopLeft.y + text.size.height / 2f)
                drawLine(accent, labelCenter, target, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(accent, radius = 5.dp.toPx(), center = target)
                drawRoundRect(
                    accent,
                    topLeft = labelTopLeft - Offset(padding, padding / 2),
                    size = Size(text.size.width + padding * 2, text.size.height + padding),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
                drawText(text, topLeft = labelTopLeft)
            }
        }
        Text(
            item.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** A stand-in for the learner's photo of a whiteboard sketch: two boxes and a shared line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWhiteboardPhoto() {
    drawRect(Color(0xFFE9E7E1))
    val marker = Color(0xFF2B4C8C)
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    val box = Size(size.width * 0.26f, size.height * 0.22f)
    drawRoundRect(marker, Offset(size.width * 0.08f, size.height * 0.3f), box, style = stroke)
    drawRoundRect(marker, Offset(size.width * 0.66f, size.height * 0.3f), box, style = stroke)
    drawLine(marker, Offset(size.width * 0.34f, size.height * 0.41f), Offset(size.width * 0.66f, size.height * 0.41f), strokeWidth = 3.dp.toPx())
    drawCircle(marker, radius = size.minDimension * 0.09f, center = Offset(size.width * 0.5f, size.height * 0.72f), style = stroke)
    drawLine(marker, Offset(size.width * 0.5f, size.height * 0.41f), Offset(size.width * 0.5f, size.height * 0.63f), strokeWidth = 3.dp.toPx())
}

@Composable
private fun JobCard(item: TranscriptItem.JobCard, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(item.kind.icon), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(item.kind.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                }
            }
            when (val status = item.status) {
                is JobStatus.Pending -> {
                    LinearWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            status.activity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {}) { Text(stringResource(R.string.abort_job)) }
                    }
                }
                JobStatus.Done -> when (item.kind) {
                    JobKind.Visual -> ModularClockVisual(Modifier.fillMaxWidth().aspectRatio(1.6f))
                    JobKind.Demo -> OutlinedButton(onClick = {}) {
                        Icon(painterResource(R.drawable.ic_open_in_full), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.open_demo), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    else -> Text(stringResource(R.string.job_done), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is JobStatus.Failed -> Text(status.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** A generated visual for the mockups: arithmetic mod 12 as a clock, 9 + 5 landing on 2. */
@Composable
private fun ModularClockVisual(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface)
    Canvas(modifier.semantics { contentDescription = "9 + 5 ≡ 2 (mod 12)" }) {
        val radius = size.minDimension * 0.4f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(colors.outline, radius, center, style = Stroke(1.5.dp.toPx()))
        fun point(hour: Int, r: Float = radius) = (2 * PI * hour / 12 - PI / 2).let {
            Offset(center.x + r * cos(it).toFloat(), center.y + r * sin(it).toFloat())
        }
        drawArc(
            colors.primary,
            startAngle = 360f * 9 / 12 - 90f,
            sweepAngle = 360f * 5 / 12,
            useCenter = false,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
        )
        for (hour in 0 until 12) {
            val text = measurer.measure(hour.toString(), style)
            val at = point(hour, radius * 0.78f)
            drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
            drawCircle(if (hour == 9 || hour == 2) colors.primary else colors.outline, 4.dp.toPx(), point(hour))
        }
    }
}

/** The running Turn: an expressive loading indicator at the end of the transcript. */
@Composable
fun RunningTurnIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.turn_running)
    LoadingIndicator(modifier.size(48.dp).semantics { contentDescription = description })
}
