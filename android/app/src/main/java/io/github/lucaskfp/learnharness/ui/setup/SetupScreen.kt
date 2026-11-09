package io.github.lucaskfp.learnharness.ui.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.lucaskfp.learnharness.R
import io.github.lucaskfp.learnharness.ui.components.DropdownField

/**
 * First-run Setup on one scrolling screen: pick a provider, enter and test the key (or a custom
 * endpoint), then the default model for each role.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: SetupState,
    modifier: Modifier = Modifier,
    onProviderSelect: (Provider) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onBaseUrlChange: (String) -> Unit = {},
    onModelNameChange: (String) -> Unit = {},
    onTestKey: () -> Unit = {},
    onModelSelect: (ModelRole, String) -> Unit = { _, _ -> },
    onContinue: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Section(stringResource(R.string.setup_provider)) {
                ProviderButtonGroup(selected = state.provider, onSelect = onProviderSelect)
            }
            if (state.provider != null) {
                Section(null) {
                    CredentialFields(state, onApiKeyChange, onBaseUrlChange, onModelNameChange)
                    KeyTestRow(state, onTestKey)
                }
            }
            Section(stringResource(R.string.setup_models)) {
                ModelFields(state, onModelSelect)
            }
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.setup_continue))
            }
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

/**
 * The providers as one connected button group. When the longest label would not fit on one
 * line in a quarter of the width, the group reflows into two connected rows of two.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderButtonGroup(selected: Provider?, onSelect: (Provider) -> Unit) {
    val labels = Provider.entries.associateWith { stringResource(it.label) }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val widestLabel = with(density) {
            labels.values.maxOf { measurer.measure(it, style).size.width }.toDp()
        }
        val perButton = (maxWidth - gap * (Provider.entries.size - 1)) / Provider.entries.size
        val rows = if (widestLabel + ProviderLabelPadding * 2 <= perButton) {
            listOf(Provider.entries)
        } else {
            Provider.entries.chunked(2)
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { index, provider ->
                        ToggleButton(
                            checked = provider == selected,
                            onCheckedChange = { onSelect(provider) },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = ProviderLabelPadding),
                        ) {
                            Text(labels.getValue(provider), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private val ProviderLabelPadding = 16.dp

@Composable
private fun CredentialFields(
    state: SetupState,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
) {
    val custom = state.provider == Provider.Custom
    if (custom) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text(stringResource(R.string.setup_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChange,
        label = {
            Text(stringResource(if (custom) R.string.setup_api_key_optional else R.string.setup_api_key))
        },
        singleLine = true,
        isError = state.keyStatus == KeyStatus.Invalid,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (custom) {
        OutlinedTextField(
            value = state.customModelName,
            onValueChange = onModelNameChange,
            label = { Text(stringResource(R.string.setup_model_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The key's test status on the left, the Test key button on the right. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeyTestRow(state: SetupState, onTestKey: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.keyStatus) {
                KeyStatus.Untested -> Unit
                KeyStatus.Testing -> {
                    LoadingIndicator(Modifier.size(StatusIconSize))
                    StatusText(R.string.setup_key_testing, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                KeyStatus.Valid -> {
                    StatusIcon(R.drawable.ic_check_circle, MaterialTheme.colorScheme.onSurface)
                    StatusText(R.string.setup_key_valid, MaterialTheme.colorScheme.onSurface)
                }
                KeyStatus.Invalid -> {
                    StatusIcon(R.drawable.ic_error, MaterialTheme.colorScheme.error)
                    StatusText(R.string.setup_key_invalid, MaterialTheme.colorScheme.error)
                }
            }
        }
        val canTest = state.keyStatus != KeyStatus.Testing &&
            (state.apiKey.isNotEmpty() || state.provider == Provider.Custom && state.baseUrl.isNotEmpty())
        FilledTonalButton(onClick = onTestKey, enabled = canTest) {
            Text(stringResource(R.string.setup_test_key), maxLines = 1)
        }
    }
}

@Composable
private fun StatusIcon(@DrawableRes icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(StatusIconSize))
}

@Composable
private fun StatusText(@StringRes text: Int, color: Color) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color)
}

private val StatusIconSize = 24.dp

/** One dropdown per role; locked until the models are known. */
@Composable
private fun ModelFields(state: SetupState, onModelSelect: (ModelRole, String) -> Unit) {
    val models = state.availableModels
    val unlocked = models.isNotEmpty()
    Text(
        text = stringResource(if (unlocked) R.string.setup_models_hint else R.string.setup_models_locked),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelRole.entries.forEach { role ->
        DropdownField(
            label = stringResource(role.label),
            value = state.modelFor(role),
            options = models,
            enabled = unlocked,
            onSelect = { onModelSelect(role, models[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
