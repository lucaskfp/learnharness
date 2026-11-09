package io.github.lucaskfp.learnharness

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.ui.LearnHarnessApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = PHONE)
class NavigationShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensOnSessions() {
        compose.setContent { LearnHarnessApp() }

        compose.onNodeWithText("No Sessions yet").assertExists()
    }

    @Test
    fun phoneUsesNavigationBarWithoutLogo() {
        compose.setContent { LearnHarnessApp() }

        compose.onNodeWithContentDescription("LearnHarness").assertDoesNotExist()
        compose.onNodeWithText("Graph").assertIsBelow(compose.onNodeWithText("No Sessions yet"))
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun mediumWidthUsesNavigationRail() {
        compose.setContent { LearnHarnessApp() }

        compose.onNodeWithText("Graph").assertIsLeftOf(compose.onNodeWithText("No Sessions yet"))
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletUsesNavigationRailWithLogo() {
        compose.setContent { LearnHarnessApp() }

        // The scaffold composes its header slot twice and places only the rail's copy.
        val marks = compose.onAllNodesWithContentDescription("LearnHarness")
        val count = marks.fetchSemanticsNodes().size
        assertTrue("expected the mark on screen", (0 until count).any { marks[it].isDisplayed() })
        compose.onNodeWithText("Graph").assertIsLeftOf(compose.onNodeWithText("No Sessions yet"))
    }

    @Test
    fun navigatesBetweenTopLevelDestinations() {
        compose.setContent { LearnHarnessApp() }

        compose.onNodeWithText("Graph").performClick()
        compose.onNodeWithText("Your knowledge graph is empty").assertExists()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Nothing to configure yet").assertExists()

        compose.onNodeWithText("Sessions").performClick()
        compose.onNodeWithText("No Sessions yet").assertExists()
    }

    @Test
    fun backFromAnotherDestinationReturnsToSessions() {
        compose.setContent { LearnHarnessApp() }
        compose.onNodeWithText("Graph").performClick()
        // NavDisplay ignores Back until its enter transition has finished.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(TRANSITION_MILLIS)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("No Sessions yet").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR-$PHONE")
    fun followsBrazilianPortuguese() {
        compose.setContent { LearnHarnessApp() }

        compose.onNodeWithText("Nenhuma sessão ainda").assertExists()
        compose.onNodeWithText("Grafo").performClick()
        compose.onNodeWithText("Seu grafo de conhecimento está vazio").assertExists()
    }
}

const val PHONE = "w411dp-h891dp-port"
const val MEDIUM = "w700dp-h1000dp-port"
const val TABLET = "w1280dp-h800dp-land"
private const val TRANSITION_MILLIS = 1_000L

private fun SemanticsNodeInteraction.bounds(): Rect = getBoundsInRoot().let {
    Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
}

private fun SemanticsNodeInteraction.assertIsBelow(other: SemanticsNodeInteraction) =
    assertTrue("expected node below the other", bounds().top >= other.bounds().bottom)

private fun SemanticsNodeInteraction.assertIsLeftOf(other: SemanticsNodeInteraction) =
    assertTrue("expected node left of the other", bounds().right <= other.bounds().left)
