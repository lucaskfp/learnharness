package io.github.lucaskfp.learnharness.ui.teaching

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.EngineHarness
import io.github.lucaskfp.learnharness.engine.probeArguments
import io.github.lucaskfp.learnharness.engine.text
import io.github.lucaskfp.learnharness.engine.toolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * What the learner typed but did not send survives the process: the Teaching Session screen's
 * state holder saves it through the engine, and a new process restores it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SessionDraftsTest {
    private val harness = EngineHarness()
    private val screens = CoroutineScope(Job() + Dispatchers.Main)

    @After
    fun tearDown() {
        screens.cancel()
        harness.close()
    }

    /** Runs the main thread (and its delayed work) until [condition] holds. */
    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(10)
        }
    }

    /** Opens the Session's screen state holder, as the screen does while it is shown. */
    private fun open(engineHarness: EngineHarness, id: String): SessionViewModel =
        SessionViewModel(engineHarness.engine, id).also { model -> screens.launch { model.state.collect {} } }

    @Test
    fun anUnsentAnswerAndItsThinkingComeBackAfterTheProcessIsKilled() {
        harness.provider.reply(
            toolCall("toolu_1", "probe", probeArguments("What does a derivative measure?", listOf("Slope" to "slope", "Area" to "area"), correct = "slope")),
        )
        val id = harness.startTeaching()
        harness.settled(id)

        val before = open(harness, id)
        pumpUntil { before.state.value?.question != null }
        before.onSelectOption(before.state.value!!.question!!.options.indexOfFirst { it.text == "Slope" })
        before.onConfidenceChange(ConfidenceStops.indexOf(90))
        before.onThinkingChange("It is the rate of change at a point")
        pumpUntil { runBlocking { harness.engine.drafts(id) }.answers["toolu_1"]?.thinking == "It is the rate of change at a point" }

        harness.stopEngine()
        val reopened = EngineHarness(provider = harness.provider, database = harness.database)
        val after = open(reopened, id)
        pumpUntil { after.state.value?.question?.thinking == "It is the rate of change at a point" }

        val question = after.state.value!!.question!!
        assertEquals(mapOf("Slope" to OptionState.Selected, "Area" to OptionState.Idle), question.options.associate { it.text to it.state })
        assertEquals(ConfidenceStops.indexOf(90), question.confidenceIndex)
    }

    @Test
    fun anUnsentComposerMessageComesBackAfterTheProcessIsKilled() {
        harness.provider.reply(text("Hello."))
        val id = harness.startTeaching()
        harness.settled(id)

        val before = open(harness, id)
        pumpUntil { before.state.value != null }
        before.onComposerTextChange("Could you also show")
        pumpUntil { runBlocking { harness.engine.drafts(id) }.composer == "Could you also show" }

        harness.stopEngine()
        val reopened = EngineHarness(provider = harness.provider, database = harness.database)
        val after = open(reopened, id)

        pumpUntil { after.state.value?.composerText == "Could you also show" }
    }
}
