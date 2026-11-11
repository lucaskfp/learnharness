package io.github.lucaskfp.learnharness.engine.store

import io.github.lucaskfp.learnharness.engine.model.LhJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** What Setup saves: the provider, its key, and the default model for each role. */
data class ModelSetup(
    val provider: String,
    val apiKey: String,
    /** Default model per role: `teaching`, `practice`, `jobs`. */
    val roleModels: Map<String, String>,
    /** The models the key can use, newest first: the choices when starting a Session. */
    val models: List<String> = emptyList(),
)

/** The learner's saved configuration. The API key is only ever stored encrypted. */
class SettingsRepository(private val dao: SettingDao, private val vault: KeyVault) {
    /** Whether first-run Setup has been completed. */
    fun observeConfigured(): Flow<Boolean> = dao.observeAll().map { rows -> rows.any { it.key == ProviderKey } }

    suspend fun save(setup: ModelSetup) {
        val rows = buildList {
            add(SettingRow(ProviderKey, setup.provider))
            add(SettingRow(secretKey(setup.provider), vault.encrypt(setup.apiKey)))
            add(SettingRow(ModelsKey, LhJson.encodeToString(ListSerializer(String.serializer()), setup.models)))
            setup.roleModels.forEach { (role, model) -> add(SettingRow(roleKey(role), model)) }
        }
        dao.putAll(rows)
    }

    suspend fun provider(): String? = dao.get(ProviderKey)

    suspend fun apiKey(provider: String): String? = dao.get(secretKey(provider))?.let(vault::decrypt)

    suspend fun roleModel(role: String): String? = dao.get(roleKey(role))

    suspend fun models(): List<String> =
        dao.get(ModelsKey)?.let { LhJson.decodeFromString(ListSerializer(String.serializer()), it) }.orEmpty()

    private fun secretKey(provider: String) = "secret.$provider"

    private fun roleKey(role: String) = "model.$role"

    private companion object {
        const val ProviderKey = "provider"
        const val ModelsKey = "models"
    }
}
