@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.teaching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R

/**
 * The composer: the message field, and beside it a floating toolbar with attach and dictate and
 * Send as its FAB. While a Turn runs, a message steers the lesson and the FAB stops the Turn.
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
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
                    Text(
                        stringResource(if (turnRunning) R.string.composer_steer else R.string.composer_hint),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = if (turnRunning) onStop else onSend) {
                    if (turnRunning) {
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
