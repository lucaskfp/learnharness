package io.github.lucaskfp.learnharness

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.app.AppContainer
import io.github.lucaskfp.learnharness.engine.AndroidAssetSource
import io.github.lucaskfp.learnharness.engine.DefaultSessionEngine
import io.github.lucaskfp.learnharness.engine.EngineEnvironment
import io.github.lucaskfp.learnharness.engine.ScriptedProvider
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.probeArguments
import io.github.lucaskfp.learnharness.engine.provider.KeyCheck
import io.github.lucaskfp.learnharness.engine.store.KeyVault
import io.github.lucaskfp.learnharness.engine.store.LearnHarnessDatabase
import io.github.lucaskfp.learnharness.engine.store.SettingsRepository
import io.github.lucaskfp.learnharness.engine.text
import io.github.lucaskfp.learnharness.engine.toolCall
import io.github.lucaskfp.learnharness.ui.AppRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The wired app from a fresh install to an answered question: Setup, the Sessions list, New Session
 * and the Probe. Only the model and the key check are scripted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = PHONE)
class AppRootTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val provider = ScriptedProvider()
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LearnHarnessDatabase::class.java,
    ).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = SettingsRepository(database.settings(), PrefixVault)
    private var testedKeys = mutableListOf<String>()
    private val container = AppContainer(
        settings = settings,
        engine = DefaultSessionEngine(
            database = database,
            assets = AndroidAssetSource(ApplicationProvider.getApplicationContext<android.content.Context>().assets),
            environment = EngineEnvironment(provider = { provider }),
            scope = scope,
        ),
        keyTester = { _, key ->
            testedKeys += key
            if (key == "sk-ant-good") KeyCheck.Valid(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"))
            else KeyCheck.Invalid("invalid x-api-key")
        },
    )

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aFreshInstallGoesFromSetupToTheFirstProbeQuestionAndAnswersIt() {
        provider.reply(text("Let's see where you stand."), toolCall("toolu_1", "probe", probeArguments(
            "What does a derivative measure?",
            listOf("The slope of the tangent line" to "slope", "The area under the curve" to "area"),
            correct = "slope",
        )))
        provider.reply(text("Good. Onwards."))
        compose.setContent { AppRoot(container) }

        // Setup: provider, a wrong key, then the right one.
        waitForText("Anthropic")
        compose.onNodeWithText("Anthropic").performClick()
        compose.onNodeWithText("API key").performTextInput("sk-ant-bad")
        compose.onNodeWithText("Test key").performClick()
        waitForText("The provider rejected this key. Check it and try again.")
        compose.onNodeWithText("API key").performTextReplacement("sk-ant-good")
        compose.onNodeWithText("Test key").performClick()
        waitForText("Key works")
        compose.onNodeWithText("Continue").performClick()

        // The key is saved encrypted and Setup is gone.
        waitForText("No Sessions yet")
        assertEquals(listOf("sk-ant-bad", "sk-ant-good"), testedKeys)

        // New Session: a goal, then Start.
        compose.onNodeWithContentDescription("New Session").performClick()
        compose.onNodeWithText("New Teaching Session").performClick()
        compose.onNodeWithText("What do you want to understand?").performTextInput("I want to understand derivatives")
        compose.onNodeWithText("Start teaching").performClick()

        // The Session: the goal, the Prober's words and its question in the dock.
        waitForText("What does a derivative measure?")
        compose.onNodeWithText("Let's see where you stand.").assertExists()
        compose.onNodeWithText("The area under the curve").assertExists()

        // Answer it: the verdict is graded on the device and the next Turn runs.
        compose.onNodeWithText("The slope of the tangent line").performClick()
        compose.onNodeWithText("Submit").performClick()
        waitForText("Good. Onwards.")
        val seen = provider.requests.last().messages.filterIsInstance<ToolResultMessage>().single()
        assertTrue(seen.content.single().text.contains("\"correct\":true"))
    }

    private object PrefixVault : KeyVault {
        override fun encrypt(plain: String) = "enc:$plain"

        override fun decrypt(encrypted: String) = encrypted.removePrefix("enc:")
    }
}
