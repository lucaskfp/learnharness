package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.RichMarkdown
import io.github.lucaskfp.learnharness.ui.components.truncatedByDesign

/**
 * The active practice problem, docked at the bottom in place of the composer (as on desktop): the
 * statement, the learner's "Your thinking" with dictation, and Submit thinking. One field for every
 * submission: whether it is a working trace or claims an answer is read from the text. Collapsed,
 * it is a one-line bar that reopens it; the learner collapses and expands it from its header.
 */
@Composable
fun PracticeDock(
    problem: PracticeProblem,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit = {},
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
        if (problem.collapsed) CollapsedProblem(problem, onToggle) else ExpandedProblem(problem, onToggle, onThinkingChange, onDictate, onSubmit)
    }
}

@Composable
private fun CollapsedProblem(problem: PracticeProblem, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.expand), role = Role.Button, onClick = onExpand)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProblemTag()
        Text(
            text = problem.statement,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).truncatedByDesign(),
        )
        IconButton(onClick = onExpand) {
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = stringResource(R.string.expand),
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

@Composable
private fun ExpandedProblem(
    problem: PracticeProblem,
    onCollapse: () -> Unit,
    onThinkingChange: (String) -> Unit,
    onDictate: () -> Unit,
    onSubmit: () -> Unit,
) {
    // The dock never takes the whole screen: the work above stays in view.
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.7f).toDp() }
    Column(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.collapse), role = Role.Button, onClick = onCollapse),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProblemTag()
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = stringResource(R.string.collapse),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        RichMarkdown(problem.statement)
        OutlinedTextField(
            value = problem.thinking,
            onValueChange = onThinkingChange,
            label = { Text(stringResource(R.string.your_thinking)) },
            placeholder = { Text(stringResource(R.string.your_thinking_hint)) },
            minLines = 3,
            trailingIcon = {
                IconButton(onClick = onDictate) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.dictate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSubmit, enabled = problem.thinking.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.submit_thinking))
        }
    }
}

@Composable
private fun ProblemTag() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            stringResource(R.string.problem),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
