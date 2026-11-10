@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.annotation.DrawableRes
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
    onDontKnow: () -> Unit = {},
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
                question,
                deepThinking,
                QuestionDockEvents(onSelect, onDontKnow, onConfidenceChange, onAnswerChange, onThinkingChange, onDictate, onSubmit),
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

private class QuestionDockEvents(
    val onSelect: (Int) -> Unit,
    val onDontKnow: () -> Unit,
    val onConfidenceChange: (Int) -> Unit,
    val onAnswerChange: (String) -> Unit,
    val onThinkingChange: (String) -> Unit,
    val onDictate: () -> Unit,
    val onSubmit: () -> Unit,
)

@Composable
private fun ExpandedDock(question: PendingQuestion, deepThinking: Boolean, events: QuestionDockEvents): Unit = with(events) {
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
            // As on desktop, free recall carries no confidence: the answer in the learner's own
            // words is the evidence.
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
                    QuestionOption(
                        stringResource(R.string.i_dont_know),
                        if (question.dontKnow) OptionState.Selected else OptionState.Idle,
                    ),
                    onClick = onDontKnow,
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

/** How an option looks in each [OptionState]: one place, shared by the dock and answered cards. */
private class OptionStyle(
    val checked: Boolean,
    val container: Color,
    val content: Color,
    val border: BorderStroke?,
    @DrawableRes val icon: Int?,
    val iconTint: Color,
    val stateLabel: String?,
)

@Composable
private fun optionStyle(state: OptionState): OptionStyle {
    val colors = MaterialTheme.colorScheme
    val correct = correctColor
    return when (state) {
        OptionState.Idle -> OptionStyle(
            checked = false, container = colors.surfaceContainerHighest, content = colors.onSurface,
            border = null, icon = null, iconTint = colors.onSurface, stateLabel = null,
        )
        OptionState.Selected -> OptionStyle(
            checked = true, container = colors.primary, content = colors.onPrimary,
            border = null, icon = R.drawable.ic_check_circle, iconTint = colors.onPrimary,
            stateLabel = stringResource(R.string.option_selected),
        )
        OptionState.Correct -> OptionStyle(
            checked = true, container = correct.copy(alpha = 0.18f).compositeOver(colors.surfaceContainer), content = correct,
            border = BorderStroke(1.5.dp, correct), icon = R.drawable.ic_check, iconTint = correct,
            stateLabel = stringResource(R.string.option_correct),
        )
        OptionState.Incorrect -> OptionStyle(
            checked = true, container = colors.errorContainer, content = colors.onErrorContainer,
            border = BorderStroke(1.5.dp, colors.error), icon = R.drawable.ic_close, iconTint = colors.error,
            stateLabel = stringResource(R.string.option_incorrect),
        )
        OptionState.MissedCorrect -> OptionStyle(
            checked = false, container = colors.surfaceContainerHighest, content = colors.onSurface,
            border = BorderStroke(1.5.dp, correct), icon = R.drawable.ic_check, iconTint = correct,
            stateLabel = stringResource(R.string.option_missed_correct),
        )
    }
}

// Expressive shape morph: round at rest, square once picked, squarer still while pressed.
private val OptionShape = RoundedCornerShape(24.dp)
private val OptionCheckedShape = RoundedCornerShape(10.dp)
private val OptionPressedShape = RoundedCornerShape(6.dp)
private val OptionPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * One answer option in the dock, an Expressive toggle button: round at rest, it morphs square
 * when picked.
 */
@Composable
fun QuizOption(option: QuestionOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = optionStyle(option.state)
    ToggleButton(
        checked = style.checked,
        onCheckedChange = { onClick() },
        shapes = ToggleButtonShapes(shape = OptionShape, pressedShape = OptionPressedShape, checkedShape = OptionCheckedShape),
        colors = ToggleButtonDefaults.colors(
            containerColor = style.container,
            contentColor = style.content,
            checkedContainerColor = style.container,
            checkedContentColor = style.content,
        ),
        border = style.border,
        contentPadding = OptionPadding,
        modifier = modifier
            .fillMaxWidth()
            .semantics { style.stateLabel?.let { stateDescription = it } },
    ) {
        OptionContent(option.text, style, Modifier.weight(1f))
    }
}

/**
 * A graded option in an answered card: the toggle's end state, read only. A picked option keeps
 * its square shape and turns green (correct) or error (incorrect); a correct option the learner
 * missed stays round with a green outline.
 */
@Composable
fun AnsweredOption(option: QuestionOption, modifier: Modifier = Modifier) {
    val style = optionStyle(option.state)
    Surface(
        shape = if (style.checked) OptionCheckedShape else OptionShape,
        color = style.container,
        contentColor = style.content,
        border = style.border,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { style.stateLabel?.let { stateDescription = it } },
    ) {
        Row(Modifier.padding(OptionPadding)) { OptionContent(option.text, style, Modifier.weight(1f)) }
    }
}

@Composable
private fun OptionContent(text: String, style: OptionStyle, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        style.icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = style.iconTint, modifier = Modifier.size(20.dp))
        }
    }
}
