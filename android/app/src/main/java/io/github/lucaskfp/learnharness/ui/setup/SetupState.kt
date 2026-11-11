package io.github.lucaskfp.learnharness.ui.setup

import androidx.annotation.StringRes
import io.github.lucaskfp.learnharness.R

/** The model providers a learner can set up. [Custom] is any OpenAI-compatible endpoint. */
enum class Provider(@StringRes val label: Int) {
    Anthropic(R.string.provider_anthropic),
    OpenAI(R.string.provider_openai),
    Gemini(R.string.provider_gemini),
    Custom(R.string.provider_custom),
}

/** The roles that each get a default model. */
enum class ModelRole(@StringRes val label: Int) {
    Teaching(R.string.role_teaching),
    Practice(R.string.role_practice),
    Jobs(R.string.role_jobs),
}

/** Where the key's test call stands. */
enum class KeyStatus { Untested, Testing, Valid, Invalid }

/** Everything the first-run Setup screen shows. */
data class SetupState(
    val provider: Provider? = null,
    val apiKey: String = "",
    val keyStatus: KeyStatus = KeyStatus.Untested,
    /** Custom endpoint only. */
    val baseUrl: String = "",
    /** Custom endpoint only: the one model the endpoint serves. */
    val customModelName: String = "",
    /** The provider's models, known once the key has been tested. */
    val models: List<String> = emptyList(),
    val roleModels: Map<ModelRole, String> = emptyMap(),
    /** The chosen provider cannot be used yet, whatever the key. */
    val providerUnavailable: Boolean = false,
) {
    val canContinue: Boolean get() = keyStatus == KeyStatus.Valid

    /** A custom endpoint serves the one model it names; other providers list theirs once tested. */
    val availableModels: List<String>
        get() = if (provider == Provider.Custom) listOf(customModelName).filter { it.isNotBlank() } else models

    /** The model chosen for [role]; on a custom endpoint, its one model unless another was chosen. */
    fun modelFor(role: ModelRole): String =
        roleModels[role] ?: if (provider == Provider.Custom) customModelName else ""
}
