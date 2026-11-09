@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.lucaskfp.learnharness.ui.newsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.DropdownField
import io.github.lucaskfp.learnharness.ui.sessions.SessionKind

/**
 * Starting a Session: the goal, then the model, teaching language and attachments. The split
 * button starts the Session; its menu switches between Teaching and Practice.
 */
@Composable
fun NewSessionScreen(
    state: NewSessionState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onGoalChange: (String) -> Unit = {},
    onModelSelect: (String) -> Unit = {},
    onLanguageSelect: (TeachingLanguage) -> Unit = {},
    onAttach: () -> Unit = {},
    onRemoveAttachment: (Attachment) -> Unit = {},
    onKindMenuToggle: (Boolean) -> Unit = {},
    onKindSelect: (SessionKind) -> Unit = {},
    onStart: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_session_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                StartSplitButton(state, onStart, onKindMenuToggle, onKindSelect)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.goal,
                onValueChange = onGoalChange,
                label = {
                    Text(
                        stringResource(
                            if (state.kind == SessionKind.Teaching) R.string.goal_teaching else R.string.goal_practice,
                        ),
                    )
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.model),
                value = modelLabel(state.model, state.defaultModel),
                options = state.models.map { modelLabel(it, state.defaultModel) },
                onSelect = { onModelSelect(state.models[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.teaching_language),
                value = stringResource(state.teachingLanguage.label),
                options = TeachingLanguage.entries.map { stringResource(it.label) },
                onSelect = { onLanguageSelect(TeachingLanguage.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Attachments(state.attachments, onAttach, onRemoveAttachment)
        }
    }
}

@Composable
private fun modelLabel(model: String, defaultModel: String): String =
    if (model == defaultModel) stringResource(R.string.model_default, model) else model

@Composable
private fun Attachments(
    attachments: List<Attachment>,
    onAttach: () -> Unit,
    onRemove: (Attachment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onAttach) {
            Icon(
                painterResource(R.drawable.ic_attach_file),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(stringResource(R.string.attach), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (attachments.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { attachment ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(attachment) },
                        label = { Text(attachment.name, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                painterResource(attachment.type.icon),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.remove_attachment, attachment.name),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Primary action starts the Session; the trailing menu picks Teaching or Practice. */
@Composable
private fun StartSplitButton(
    state: NewSessionState,
    onStart: () -> Unit,
    onKindMenuToggle: (Boolean) -> Unit,
    onKindSelect: (SessionKind) -> Unit,
) {
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(onClick = onStart, enabled = state.canStart) {
                Icon(
                    painterResource(state.kind.icon),
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                )
                Text(
                    stringResource(
                        if (state.kind == SessionKind.Teaching) R.string.start_teaching else R.string.start_practice,
                    ),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    maxLines = 1,
                )
            }
        },
        trailingButton = {
            Box {
                val description = stringResource(R.string.choose_kind)
                SplitButtonDefaults.TrailingButton(
                    checked = state.kindMenuExpanded,
                    onCheckedChange = onKindMenuToggle,
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_down),
                        contentDescription = null,
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .rotate(if (state.kindMenuExpanded) 180f else 0f),
                    )
                }
                DropdownMenu(
                    expanded = state.kindMenuExpanded,
                    onDismissRequest = { onKindMenuToggle(false) },
                ) {
                    SessionKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(stringResource(kind.label)) },
                            onClick = {
                                onKindSelect(kind)
                                onKindMenuToggle(false)
                            },
                            leadingIcon = { Icon(painterResource(kind.icon), contentDescription = null) },
                            trailingIcon = if (kind == state.kind) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
    )
}
