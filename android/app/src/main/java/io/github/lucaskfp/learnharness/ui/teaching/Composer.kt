@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.theme.correctColor
import io.github.lucaskfp.learnharness.ui.theme.onCorrectColor

/**
 * The composer: the message field, and beside it a floating toolbar with attach and dictate and
 * Send as its FAB. While a Turn runs, a typed message steers the lesson (the FAB sends it) and
 * with nothing typed the FAB stops the Turn. Between practice problems ([nextProblem]), with
 * nothing typed, "Next problem" takes the toolbar's place; typing brings Send back.
 */
@Composable
fun Composer(
    text: String,
    turnRunning: Boolean,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onDictate: () -> Unit = {},
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
    nextProblem: Boolean = false,
    askAboutSolution: Boolean = false,
    onNext: () -> Unit = {},
) {
    val hint = when {
        turnRunning -> R.string.composer_steer
        askAboutSolution -> R.string.ask_about_solution
        else -> R.string.composer_hint
    }
    val showNext = nextProblem && !turnRunning && text.isBlank()
    val outer = modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    // At large font scales Next no longer fits beside the field: it goes below, at the end.
    if (showNext && LocalDensity.current.fontScale > StackedNextFontScale) {
        Column(outer, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageField(text, hint, onTextChange, Modifier.fillMaxWidth())
            NextProblemButton(onNext, Modifier.align(Alignment.End))
        }
        return
    }
    Row(
        modifier = outer,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageField(text, hint, onTextChange, Modifier.weight(1f))
        if (showNext) {
            NextProblemButton(onNext)
            return@Row
        }
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                val stops = turnRunning && text.isBlank()
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = if (stops) onStop else onSend) {
                    if (stops) {
                        Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.stop_turn))
                    } else {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.send))
                    }
                }
            },
        ) {
            IconButton(onClick = onAttach) {
                Icon(painterResource(R.drawable.ic_attach_file), contentDescription = stringResource(R.string.attach))
            }
            IconButton(onClick = onDictate) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.dictate))
            }
        }
    }
}

/** Above this font scale the Next problem button stacks under the message field. */
private const val StackedNextFontScale = 1.3f

/** The message field, with [hint] while it is empty. */
@Composable
private fun MessageField(text: String, @StringRes hint: Int, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.CenterStart) {
            val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(stringResource(hint), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The learner's go-ahead between practice problems, in the correct-answer green. */
@Composable
private fun NextProblemButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = correctColor, contentColor = onCorrectColor),
        contentPadding = PaddingValues(start = 20.dp, end = 16.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.next_problem), style = MaterialTheme.typography.labelLarge)
        Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.padding(start = 8.dp))
    }
}
