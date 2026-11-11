package io.github.lucaskfp.learnharness.app

import android.content.Context
import androidx.room.Room
import io.github.lucaskfp.learnharness.engine.AndroidAssetSource
import io.github.lucaskfp.learnharness.engine.DefaultSessionEngine
import io.github.lucaskfp.learnharness.engine.EngineEnvironment
import io.github.lucaskfp.learnharness.engine.SessionEngine
import io.github.lucaskfp.learnharness.engine.provider.AnthropicProvider
import io.github.lucaskfp.learnharness.engine.provider.KeyCheck
import io.github.lucaskfp.learnharness.engine.store.AndroidKeystoreKeyVault
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import io.github.lucaskfp.learnharness.engine.store.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Why a key could not be used. */
fun interface KeyTester {
    suspend fun check(provider: String, apiKey: String): KeyCheck
}

/** What the screens need from the engine side of the app, wired once (a hand-rolled composition root). */
class AppContainer(
    val settings: SettingsRepository,
    val engine: SessionEngine,
    val keyTester: KeyTester,
    val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val database = Room.databaseBuilder(app, LearnHarnessDatabase::class.java, "learnharness.db").build()
            val settings = SettingsRepository(database.settings(), AndroidKeystoreKeyVault())
            val engine = DefaultSessionEngine(
                database = database,
                assets = AndroidAssetSource(app.assets),
                environment = EngineEnvironment(
                    provider = { providerId ->
                        val key = settings.apiKey(providerId) ?: error("No API key is set up")
                        AnthropicProvider(key)
                    },
                ),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
            return AppContainer(
                settings = settings,
                engine = engine,
                keyTester = { _, key -> AnthropicProvider(key).checkKey() },
            )
        }
    }
}
