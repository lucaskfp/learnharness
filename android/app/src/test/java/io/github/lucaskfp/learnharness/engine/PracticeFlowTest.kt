package io.github.lucaskfp.learnharness.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Practice Session's problem/conclude cycle, through the engine's public interface. Only the
 * model is scripted; the Practitioner's tools, the one-active-problem gate and Room are real.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PracticeFlowTest {
    private val harness = EngineHarness()
    private val provider = harness.provider

    @After
    fun tearDown() = harness.close()

    private val staircase = "A staircase has 10 steps. You climb 1 or 2 at a time. How many ways reach the top?"
    private val staircaseSolution = "ways(n) = ways(n-1) + ways(n-2), ways(1)=1, ways(2)=2: 89 ways."

    /** The Practitioner opens and poses the staircase problem (one model call). */
    private fun poseStaircase(id: String = "p_1") =
        provider.reply(text("Let's put recurrences to work."), toolCall(id, "problem", problemArguments(staircase, staircaseSolution)))

    private fun toolNames(requestIndex: Int) = provider.requests[requestIndex].tools.map { it.name }.toSet()

    @Test
    fun aPracticeSessionRunsThePractitionerFromItsFirstTurn() {
        provider.reply(text("Let's put recurrences to work."))

        val id = harness.startPractice()
        val view = harness.settled(id)

        assertEquals(SessionKind.Practice, view.kind)
        assertEquals(setOf("problem", "conclude"), toolNames(0))
        val prompt = provider.requests[0].systemPrompt
        assertTrue(prompt.contains("practice sessions"))
        assertFalse(prompt.contains("probe_done"))
        assertEquals(SessionPhase.Practice, view.phase)
    }

    @Test
    fun aPosedProblemEndsTheTurnAndBecomesTheActiveProblemWithoutItsSolution() {
        poseStaircase()

        val id = harness.startPractice()
        val view = harness.settled(id)

        assertEquals(1, provider.requests.size)
        assertEquals(ActiveProblemView("p_1", staircase), view.activeProblem)
        assertEquals(SessionPhase.ProblemOpen, view.phase)
        assertEquals(
            listOf(
                TranscriptEntry.Learner("Dynamic programming on sequences"),
                TranscriptEntry.AgentText("Let's put recurrences to work."),
                TranscriptEntry.Problem("p_1", staircase),
            ),
            view.transcript,
        )
        assertFalse(view.toString().contains(staircaseSolution))
        assertNull(view.pendingQuestion)
    }

    // ─── the one-active-problem gate ────────────────────────────────────────────────────

    @Test
    fun aSecondPoseInTheSameMessageIsRefusedWhileTheFirstIsActive() {
        provider.reply(
            toolCall("p_1", "problem", problemArguments(staircase, staircaseSolution)),
            toolCall("p_2", "problem", problemArguments("Another problem", "Another solution")),
        )

        val id = harness.startPractice()
        val view = harness.settled(id)

        assertEquals(ActiveProblemView("p_1", staircase), view.activeProblem)
        assertEquals(listOf(TranscriptEntry.Problem("p_1", staircase)), view.transcript.filterIsInstance<TranscriptEntry.Problem>())
        assertEquals(1, provider.requests.size)
    }

    // ─── thinking ───────────────────────────────────────────────────────────────────────

    private val trace = "ways(n) = ways(n-1) + ways(n-2). Not sure where to start counting."

    private fun think(sessionId: String, problemCallId: String, text: String) = runBlocking {
        harness.engine.submit(sessionId, LearnerInput.Thinking(problemCallId, text))
    }

    @Test
    fun thinkingOnTheActiveProblemIsSavedAndRunsATurnThatReadsItAsTheLearnersThinking() {
        poseStaircase()
        provider.reply(text("Your recurrence is right. Count the smallest cases by hand."))
        val id = harness.startPractice()
        harness.settled(id)

        think(id, "p_1", trace)
        val view = harness.settled(id)

        assertEquals(2, provider.requests.size)
        val sent = provider.requests[1].messages.last() as UserMessage
        assertEquals("Your thinking:\n$trace", sent.content)
        assertEquals(
            listOf(TranscriptEntry.Thinking(trace), TranscriptEntry.AgentText("Your recurrence is right. Count the smallest cases by hand.")),
            view.transcript.takeLast(2),
        )
        // The problem stays active through hints.
        assertEquals("p_1", view.activeProblem?.toolCallId)
    }

    @Test
    fun aRefusedPoseReturnsToTheModelAsACorrectableError() {
        provider.reply(
            toolCall("p_1", "problem", problemArguments(staircase, staircaseSolution)),
            toolCall("p_2", "problem", problemArguments("Another problem", "Another solution")),
        )
        provider.reply(text("A hint."))
        val id = harness.startPractice()
        harness.settled(id)

        think(id, "p_1", trace)
        harness.settled(id)

        val refusal = provider.requests[1].messages.filterIsInstance<ToolResultMessage>().single { it.toolCallId == "p_2" }
        assertTrue(refusal.isError)
        assertTrue(refusal.content.single().text.contains("a problem is already active"))
    }

    @Test
    fun thinkingThatTargetsNoActiveProblemIsRejectedAndNothingIsSaved() {
        poseStaircase()
        val id = harness.startPractice()
        val before = harness.settled(id)

        assertThrows(NoActiveProblemException::class.java) { think(id, "p_other", trace) }

        assertEquals(before.transcript, harness.settled(id).transcript)
        assertEquals(1, provider.requests.size)
    }

    // ─── conclude ───────────────────────────────────────────────────────────────────────

    private val verdict = "You found the recurrence and fixed the base cases after one hint."

    /** Pose, a claimed answer, then a conclude and the Practitioner's closing words (model calls 2 and 3). */
    private fun solveStaircase(): String {
        poseStaircase()
        provider.reply(
            text("Correct."),
            toolCall("c_1", "conclude", concludeArguments("solvedWithHints", verdict, rung = "R1")),
        )
        provider.reply(text("Nicely done."))
        val id = harness.startPractice()
        harness.settled(id)
        think(id, "p_1", "So 89.")
        harness.settled(id)
        return id
    }

    @Test
    fun concludeRecordsTheOutcomeAndRevealsTheSolutionCommittedWhenTheProblemWasPosed() {
        val id = solveStaircase()
        val view = harness.settled(id)

        assertEquals(3, provider.requests.size)
        val ack = provider.requests[2].messages.filterIsInstance<ToolResultMessage>().single { it.toolCallId == "c_1" }
        assertFalse(ack.isError)
        assertTrue(ack.content.single().text.startsWith("Problem concluded."))
        assertEquals(
            listOf(
                TranscriptEntry.AgentText("Correct."),
                TranscriptEntry.Concluded(ProblemOutcome.SolvedWithHints, verdict, staircaseSolution),
                TranscriptEntry.AgentText("Nicely done."),
            ),
            view.transcript.takeLast(3),
        )
        assertNull(view.activeProblem)
        assertEquals(SessionPhase.ProblemConcluded, view.phase)
    }

    @Test
    fun concludeWithNoActiveProblemIsRefused() {
        provider.reply(toolCall("c_1", "conclude", concludeArguments("solved", verdict)))
        provider.reply(text("Let me pose one first."))

        val id = harness.startPractice()
        val view = harness.settled(id)

        val refusal = provider.requests[1].messages.filterIsInstance<ToolResultMessage>().single { it.toolCallId == "c_1" }
        assertTrue(refusal.isError)
        assertTrue(refusal.content.single().text.contains("no active problem to conclude"))
        assertTrue(view.transcript.none { it is TranscriptEntry.Concluded })
        assertEquals(SessionPhase.Practice, view.phase)
    }

    @Test
    fun aConcludeAndTheNextPoseInOneTurnSeeEachOther() {
        poseStaircase()
        provider.reply(
            toolCall("c_1", "conclude", concludeArguments("solved", verdict)),
            toolCall("p_2", "problem", problemArguments("Tiles of length 1 and 2 cover a 1 by 10 strip. How many ways?", "89 again.")),
        )
        val id = harness.startPractice()
        harness.settled(id)

        think(id, "p_1", "So 89.")
        val view = harness.settled(id)

        assertEquals("p_2", view.activeProblem?.toolCallId)
        assertEquals(SessionPhase.ProblemOpen, view.phase)
        assertEquals(2, provider.requests.size)
        // The reveal is the concluded problem's solution, not the one posed after it.
        assertEquals(staircaseSolution, view.transcript.filterIsInstance<TranscriptEntry.Concluded>().single().modelSolution)
    }
    // ─── next ───────────────────────────────────────────────────────────────────────────

    private fun next(sessionId: String) = runBlocking { harness.engine.submit(sessionId, LearnerInput.Next) }

    @Test
    fun nextAfterAConcludedProblemAddsTheGoAheadMarkerAndRunsATurn() {
        val id = solveStaircase()
        assertTrue(harness.settled(id).nextAllowed)
        provider.reply(toolCall("p_2", "problem", problemArguments("Tiles of length 1 and 2 cover a 1 by 10 strip. How many ways?", "89.")))

        next(id)
        val view = harness.settled(id)

        val sent = provider.requests[3].messages.last() as UserMessage
        assertTrue(sent.content.startsWith("[NEXT PROBLEM]"))
        assertEquals(TranscriptEntry.NextProblem, view.transcript[view.transcript.size - 2])
        assertEquals("p_2", view.activeProblem?.toolCallId)
        assertFalse(view.nextAllowed)
    }

    @Test
    fun nextIsRefusedWhileAProblemIsActive() {
        poseStaircase()
        val id = harness.startPractice()
        val view = harness.settled(id)

        assertFalse(view.nextAllowed)
        assertThrows(NextNotAllowedException::class.java) { next(id) }
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun nextIsRefusedBeforeTheFirstProblem() {
        provider.reply(text("What would you like to practise?"))
        val id = harness.startPractice()
        val view = harness.settled(id)

        assertFalse(view.nextAllowed)
        assertThrows(NextNotAllowedException::class.java) { next(id) }
        assertEquals(view.transcript, harness.settled(id).transcript)
    }

    @Test
    fun nextIsRefusedInATeachingSession() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        assertFalse(harness.settled(id).nextAllowed)

        assertThrows(NextNotAllowedException::class.java) { next(id) }
        assertThrows(NoActiveProblemException::class.java) { think(id, "p_1", trace) }
    }

    // ─── evidence ───────────────────────────────────────────────────────────────────────

    @Test
    fun theOutcomeIsKeptAsSessionEvidenceAndSurvivesARestart() {
        val id = solveStaircase()

        val reopened = EngineHarness(database = harness.database)
        val view = reopened.eventually(id) { true }
        reopened.stopEngine()

        assertEquals(
            TranscriptEntry.Concluded(ProblemOutcome.SolvedWithHints, verdict, staircaseSolution),
            view.transcript.filterIsInstance<TranscriptEntry.Concluded>().single(),
        )
        assertEquals(SessionPhase.ProblemConcluded, view.phase)
    }
}
