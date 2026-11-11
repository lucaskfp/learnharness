package io.github.lucaskfp.learnharness.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.store.KeyVault
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import io.github.lucaskfp.learnharness.engine.store.ModelSetup
import io.github.lucaskfp.learnharness.engine.store.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** First-run configuration: what Setup saves, and that the API key never rests in the clear. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LearnHarnessDatabase::class.java,
    ).build()
    private val settings = SettingsRepository(database.settings(), ReversingVault)

    @After
    fun tearDown() = database.close()

    private val setup = ModelSetup(
        provider = "anthropic",
        apiKey = "sk-ant-secret",
        roleModels = mapOf("teaching" to "claude-opus-5-5", "practice" to "claude-sonnet-5-5", "jobs" to "claude-haiku-5-5"),
        models = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
    )

    @Test
    fun isNotConfiguredUntilSetupIsSaved() = runBlocking {
        assertFalse(settings.observeConfigured().first())
        assertNull(settings.provider())

        settings.save(setup)

        assertTrue(settings.observeConfigured().first())
    }

    @Test
    fun savesTheProviderModelsAndRoleDefaults() = runBlocking {
        settings.save(setup)

        assertEquals("anthropic", settings.provider())
        assertEquals("claude-opus-5-5", settings.roleModel("teaching"))
        assertEquals("claude-haiku-5-5", settings.roleModel("jobs"))
        assertEquals(setup.models, settings.models())
    }

    @Test
    fun theApiKeyIsStoredOnlyThroughTheVaultAndReadBackDecrypted() = runBlocking {
        settings.save(setup)

        assertEquals("sk-ant-secret", settings.apiKey("anthropic"))
        val stored = database.settings().observeAll().first().map { it.value }
        assertTrue("the key is stored encrypted", stored.none { it.contains("sk-ant-secret") })
        assertTrue(stored.contains(ReversingVault.encrypt("sk-ant-secret")))
    }

    private object ReversingVault : KeyVault {
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()

        override fun decrypt(encrypted: String) = encrypted.removePrefix("sealed:").reversed()
    }
}
