package io.github.lucaskfp.learnharness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.lucaskfp.learnharness.ui.components.TruncatedByDesign
import io.github.lucaskfp.learnharness.ui.newsession.NewSessionScreen
import io.github.lucaskfp.learnharness.ui.preview.FakeNewSession
import io.github.lucaskfp.learnharness.ui.preview.FakeSessions
import io.github.lucaskfp.learnharness.ui.preview.FakeSetup
import io.github.lucaskfp.learnharness.ui.preview.FakeTeaching
import io.github.lucaskfp.learnharness.ui.sessions.SessionsScreen
import io.github.lucaskfp.learnharness.ui.setup.SetupScreen
import io.github.lucaskfp.learnharness.ui.teaching.PlanScreen
import io.github.lucaskfp.learnharness.ui.teaching.TeachingSessionScreen
import io.github.lucaskfp.learnharness.ui.theme.LearnHarnessTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every mockup state, rendered in Brazilian Portuguese (the longer locale) at the default and a
 * large font scale, must show all of its text: no label may be clipped or ellipsized. Text that
 * is truncated on purpose (a Session's goal) is marked [TruncatedByDesign] and skipped.
 * Every state is checked on a phone; screens with a tablet layout are checked on a tablet too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-$PHONE")
// Real text measurement: LEGACY graphics fakes it and every label reads as clipped.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MockupTextFitTest(private val mockup: Mockup, private val fontScale: Float) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allTextFits() = compose.assertAllTextFits(mockup, fontScale)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} @ {1}x")
        fun parameters() = atBothScales(setupMockups + sessionsMockups + newSessionMockups + teachingMockups + teachMockups)
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-$TABLET")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MockupTextFitTabletTest(private val mockup: Mockup, private val fontScale: Float) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allTextFits() = compose.assertAllTextFits(mockup, fontScale)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} @ {1}x")
        fun parameters() = atBothScales(sessionsMockups + teachMockups)
    }
}

class Mockup(val name: String, val content: @Composable () -> Unit) {
    override fun toString() = name
}

private val setupMockups = listOf(
    Mockup("setup-empty") { SetupScreen(FakeSetup.empty()) },
    Mockup("setup-key-testing") { SetupScreen(FakeSetup.keyTesting()) },
    Mockup("setup-key-invalid") { SetupScreen(FakeSetup.keyInvalid()) },
    Mockup("setup-key-valid") { SetupScreen(FakeSetup.keyValid()) },
    Mockup("setup-custom-endpoint") { SetupScreen(FakeSetup.customEndpoint()) },
)

private val sessionsMockups = listOf(
    Mockup("sessions-empty") { SessionsScreen(FakeSessions.empty()) },
    Mockup("sessions-populated") { SessionsScreen(FakeSessions.populated()) },
    Mockup("sessions-fab-menu") { SessionsScreen(FakeSessions.fabMenuExpanded()) },
    Mockup("sessions-search-results") { SessionsScreen(FakeSessions.searchResults()) },
    Mockup("sessions-search-no-match") { SessionsScreen(FakeSessions.searchNoMatch()) },
    Mockup("sessions-delete") { SessionsScreen(FakeSessions.deleteConfirmation()) },
)

private val newSessionMockups = listOf(
    Mockup("new-session-empty") { NewSessionScreen(FakeNewSession.emptyTeaching()) },
    Mockup("new-session-filled") { NewSessionScreen(FakeNewSession.filledTeaching()) },
    Mockup("new-session-practice") { NewSessionScreen(FakeNewSession.practice()) },
    Mockup("new-session-kind-menu") { NewSessionScreen(FakeNewSession.kindMenuOpen()) },
)

private val teachingMockups = listOf(
    Mockup("probe-multiple-choice") { TeachingSessionScreen(FakeTeaching.probeMultipleChoice()) },
    Mockup("probe-recall") { TeachingSessionScreen(FakeTeaching.probeRecall()) },
    Mockup("probe-collapsed") { TeachingSessionScreen(FakeTeaching.probeCollapsed()) },
    Mockup("probe-running") { TeachingSessionScreen(FakeTeaching.probeRunning()) },
    Mockup("plan-in-transcript") { TeachingSessionScreen(FakeTeaching.planInTranscript()) },
    Mockup("plan-full-screen") { PlanScreen(FakeTeaching.teachingPlan()) },
)

/** Teach states, which also have a tablet layout (the Plan beside the lesson). */
private val teachMockups = listOf(
    Mockup("teach-prediction") { TeachingSessionScreen(FakeTeaching.teachPrediction()) },
    Mockup("teach-explanation") { TeachingSessionScreen(FakeTeaching.teachExplanation()) },
    Mockup("teach-quiz-graded") { TeachingSessionScreen(FakeTeaching.teachQuizGraded()) },
    Mockup("teach-images") { TeachingSessionScreen(FakeTeaching.teachImages()) },
    Mockup("teach-jobs") { TeachingSessionScreen(FakeTeaching.teachJobs()) },
    Mockup("teach-running") { TeachingSessionScreen(FakeTeaching.teachRunning()) },
)

private fun atBothScales(mockups: List<Mockup>): List<Array<Any>> =
    mockups.flatMap { mockup -> listOf(1.0f, 1.5f).map { arrayOf<Any>(mockup, it) } }

private fun ComposeContentTestRule.assertAllTextFits(mockup: Mockup, fontScale: Float) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            LearnHarnessTheme { mockup.content() }
        }
    }

    val clipped = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .filterNot { it.config.getOrNull(TruncatedByDesign) == true }
        .filter { it.overflows() }
        .map { it.text() }

    assertTrue("${mockup.name} at ${fontScale}x clips: $clipped", clipped.isEmpty())
}

private fun SemanticsNode.overflows(): Boolean {
    val layouts = mutableListOf<TextLayoutResult>()
    config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.any { it.losesText() }
}

/**
 * Text is lost when it runs past its line limit or its height, is ellipsized, or has a word
 * split across lines because it did not fit. (`hasVisualOverflow` is no use here: it also counts
 * text narrower than its slot.)
 */
private fun TextLayoutResult.losesText(): Boolean {
    if (multiParagraph.didExceedMaxLines || didOverflowHeight) return true
    if ((0 until lineCount).any { isLineEllipsized(it) }) return true
    val text = layoutInput.text.text
    return (0 until lineCount - 1).any { line ->
        val end = getLineEnd(line)
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
}

private fun SemanticsNode.text(): String =
    config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        ?: config.getOrNull(SemanticsProperties.EditableText)?.text
        ?: "<node $id>"
