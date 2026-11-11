package io.github.lucaskfp.learnharness.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lucaskfp.learnharness.app.KeyTester
import io.github.lucaskfp.learnharness.engine.provider.KeyCheck
import io.github.lucaskfp.learnharness.engine.store.ModelSetup
import io.github.lucaskfp.learnharness.engine.store.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** First-run Setup: test the key with a cheap call, pick the default models, save the key encrypted. */
class SetupViewModel(
    private val settings: SettingsRepository,
    private val keyTester: KeyTester,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = mutableState.asStateFlow()

    fun onProviderSelect(provider: Provider) {
        mutableState.value = SetupState(provider = provider, providerUnavailable = provider != Provider.Anthropic)
    }

    fun onApiKeyChange(key: String) = mutableState.update {
        it.copy(apiKey = key, keyStatus = KeyStatus.Untested, models = emptyList(), roleModels = emptyMap())
    }

    fun onBaseUrlChange(url: String) = mutableState.update { it.copy(baseUrl = url, keyStatus = KeyStatus.Untested) }

    fun onModelNameChange(name: String) = mutableState.update { it.copy(customModelName = name) }

    fun onModelSelect(role: ModelRole, model: String) = mutableState.update { it.copy(roleModels = it.roleModels + (role to model)) }

    fun onTestKey() {
        val current = mutableState.value
        if (current.providerUnavailable) {
            mutableState.update { it.copy(keyStatus = KeyStatus.Invalid) }
            return
        }
        val key = current.apiKey.trim()
        mutableState.update { it.copy(keyStatus = KeyStatus.Testing) }
        viewModelScope.launch {
            val result = keyTester.check(ProviderId, key)
            mutableState.update { state ->
                if (state.apiKey.trim() != key) return@update state // the key changed while testing
                when (result) {
                    is KeyCheck.Valid -> state.copy(
                        keyStatus = KeyStatus.Valid,
                        models = result.models,
                        roleModels = defaultModels(result.models),
                    )
                    is KeyCheck.Invalid -> state.copy(keyStatus = KeyStatus.Invalid)
                }
            }
        }
    }

    /** Saves the key and the models; the app then leaves Setup because it is configured. */
    fun onContinue() {
        val current = mutableState.value
        if (!current.canContinue) return
        viewModelScope.launch {
            settings.save(
                ModelSetup(
                    provider = ProviderId,
                    apiKey = current.apiKey.trim(),
                    roleModels = ModelRole.entries.associate { it.key to current.modelFor(it) },
                    models = current.models,
                ),
            )
        }
    }

    companion object {
        private const val ProviderId = "anthropic"

        /** The newest Opus teaches, Sonnet practises and Haiku runs background Jobs, when the key can use them. */
        fun defaultModels(models: List<String>): Map<ModelRole, String> {
            fun pick(family: String) = models.firstOrNull { family in it }
            return mapOf(
                ModelRole.Teaching to (pick("opus") ?: models.firstOrNull()),
                ModelRole.Practice to (pick("sonnet") ?: models.firstOrNull()),
                ModelRole.Jobs to (pick("haiku") ?: models.firstOrNull()),
            ).mapNotNull { (role, model) -> model?.let { role to it } }.toMap()
        }
    }
}

/** The key a role is saved under. */
val ModelRole.key: String get() = name.lowercase()
