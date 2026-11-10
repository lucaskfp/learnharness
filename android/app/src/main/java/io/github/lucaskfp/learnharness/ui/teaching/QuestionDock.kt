@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.truncatedByDesign
import io.github.lucaskfp.learnharness.ui.theme.correctColor

/**
 * The pending question, docked at the bottom in place of the composer (as on desktop): the kind
 * and position, the question, options or a free-recall field, confidence, "Your thinking" with
 * dictation, and Submit. Collapsed, it is a one-line bar that reopens it.
 */
@Composable
fun QuestionDock(
    question: PendingQuestion,
    deepThinking: Boolean,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    onSelect: (Int) -> Unit = {},
    onConfidenceChange: (Int) -> Unit = {},
    onAnswerChange: (String) -> Unit = {},
    onThinkingChange: (String) -> Unit = {},
    onDictate: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 3.dp,
    ) {
        if (question.collapsed) {
            CollapsedBar(question, onExpand)
        } else {
            ExpandedDock(
                question, deepThinking, onSelect, onConfidenceChange, onAnswerChange, onThinkingChange, onDictate, onSubmit,
            )
        }
    }
}

@Composable
private fun CollapsedBar(question: PendingQuestion, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindTag(question)
        Text(
            text = question.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).truncatedByDesign(),
        )
        TextButton(onClick = onExpand) {
            Text(stringResource(R.string.answer_question))
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(180f),
            )
        }
    }
}

@Composable
private fun ExpandedDock(
    question: PendingQuestion,
    deepThinking: Boolean,
    onSelect: (Int) -> Unit,
    onConfidenceChange: (Int) -> Unit,
    onAnswerChange: (String) -> Unit,
    onThinkingChange: (String) -> Unit,
    onDictate: () -> Unit,
    onSubmit: () -> Unit,
) {
    // The dock never takes the whole screen: the lesson above stays in view.
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.7f).toDp() }
    Column(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindTag(question)
        Text(question.text, style = MaterialTheme.typography.titleMedium)
        if (question.kind.freeRecall) {
            OutlinedTextField(
                value = question.answer,
                onValueChange = onAnswerChange,
                label = { Text(stringResource(R.string.your_answer)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ToggleButtonDefaults.IconSpacing)) {
                question.options.forEachIndexed { i, option ->
                    QuizOption(option, onClick = { onSelect(i) })
                }
                QuizOption(
                    QuestionOption(stringResource(R.string.i_dont_know)),
                    onClick = { onSelect(question.options.size) },
                )
            }
            ConfidenceSlider(question.confidenceIndex, onConfidenceChange)
        }
        OutlinedTextField(
            value = question.thinking,
            onValueChange = onThinkingChange,
            label = { Text(stringResource(R.string.your_thinking)) },
            placeholder = { Text(stringResource(R.string.your_thinking_hint)) },
            // Probing stays quick; teaching invites depth (desktop).
            minLines = if (deepThinking) 3 else 1,
            trailingIcon = {
                IconButton(onClick = onDictate) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.dictate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSubmit, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.submit))
        }
    }
}

@Composable
private fun KindTag(question: PendingQuestion) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (question.count > 1) {
                stringResource(R.string.question_position, stringResource(question.kind.label), question.position, question.count)
            } else {
                stringResource(question.kind.label)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * One answer option, an Expressive toggle button: round at rest, it morphs square when picked.
 * After grading it keeps its picked shape and turns green (correct) or error (incorrect); a
 * correct option the learner missed stays round with a green outline.
 */
@Composable
fun QuizOption(option: QuestionOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val correct = correctColor
    val checked = option.state in setOf(OptionState.Selected, OptionState.Correct, OptionState.Incorrect)
    val (container, content) = when (option.state) {
        OptionState.Idle, OptionState.MissedCorrect -> colors.surfaceContainerHighest to colors.onSurface
        OptionState.Selected -> colors.primary to colors.onPrimary
        OptionState.Correct -> correct.copy(alpha = 0.18f).compositeOver(colors.surfaceContainer) to correct
        OptionState.Incorrect -> colors.errorContainer to colors.onErrorContainer
    }
    val border = when (option.state) {
        OptionState.Correct, OptionState.MissedCorrect -> BorderStroke(1.5.dp, correct)
        OptionState.Incorrect -> BorderStroke(1.5.dp, colors.error)
        else -> null
    }
    val icon = when (option.state) {
        OptionState.Selected -> R.drawable.ic_check_circle
        OptionState.Correct, OptionState.MissedCorrect -> R.drawable.ic_check
        OptionState.Incorrect -> R.drawable.ic_close
        OptionState.Idle -> null
    }
    val iconTint = when (option.state) {
        OptionState.MissedCorrect -> correct
        OptionState.Incorrect -> colors.error
        else -> Color.Unspecified
    }
    val stateLabel = when (option.state) {
        OptionState.Idle -> null
        OptionState.Selected -> stringResource(R.string.option_selected)
        OptionState.Correct -> stringResource(R.string.option_correct)
        OptionState.Incorrect -> stringResource(R.string.option_incorrect)
        OptionState.MissedCorrect -> stringResource(R.string.option_missed_correct)
    }
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = ToggleButtonShapes(
            shape = RoundedCornerShape(24.dp),
            pressedShape = RoundedCornerShape(6.dp),
            checkedShape = RoundedCornerShape(10.dp),
        ),
        colors = ToggleButtonDefaults.colors(
            containerColor = container,
            contentColor = content,
            checkedContainerColor = container,
            checkedContentColor = content,
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (stateLabel != null) stateDescription = stateLabel },
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(option.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = if (iconTint == Color.Unspecified) content else iconTint, modifier = Modifier.size(20.dp))
            }
        }
    }
}
