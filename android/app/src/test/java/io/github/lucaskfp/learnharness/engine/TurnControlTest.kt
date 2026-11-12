package io.github.lucaskfp.learnharness.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.model.StopReason
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.model.UserMessage
import io.github.lucaskfp.learnharness.engine.provider.ProviderEvent
import io.github.lucaskfp.learnharness.engine.provider.ProviderException
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The learner's control over running Turns (issue #9): abort, steer, retry, sibling questions and
 * drafts, driven through the Session engine's public interface. Only the model is scripted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TurnControlTest {
    private val harness = EngineHarness()
    private val provider = harness.provider
    private val engine = harness.engine

    @After
    fun tearDown() = harness.close()

    /** The next model call streams [partial] and then hangs until it is cancelled. */
    private fun hangAfter(partial: String): CompletableDeferred<Unit> {
        val started = CompletableDeferred<Unit>()
        provider.replyWith {
            emit(text(partial))
            started.complete(Unit)
            awaitCancellation()
        }
        return started
    }

    // ─── abort ──────────────────────────────────────────────────────────────────────────

    @Test
    fun abortingATurnKeepsItsPartialOutputMarksTheCutAndFreesTheSession() {
        val started = hangAfter("Derivatives measure")
        val id = harness.startTeaching()
        runBlocking { started.await() }

        runBlocking { engine.abort(id) }
        val aborted = harness.settled(id)

        assertFalse(aborted.turnRunning)
        assertEquals(
            listOf(
                TranscriptEntry.Learner("I want to understand derivatives"),
                TranscriptEntry.AgentText("Derivatives measure"),
                TranscriptEntry.Interrupted,
            ),
            aborted.transcript,
        )
        assertNull("an abort is not a failure", aborted.failure)

        // The Session takes a new Turn, and the model is not shown the cut-off output.
        provider.reply(text("Let's start again."))
        runBlocking { engine.submit(id, LearnerInput.Message("Start over, please")) }
        val next = harness.settled(id)
        assertEquals(TranscriptEntry.AgentText("Let's start again."), next.transcript.last())
        val sent = provider.requests.last().messages
        assertEquals(listOf("I want to understand derivatives", "Start over, please"), sent.map { (it as UserMessage).content })
    }

    @Test
    fun abortingBeforeAnyOutputStillMarksTheTurnInterrupted() {
        val started = CompletableDeferred<Unit>()
        provider.replyWith { started.complete(Unit); awaitCancellation() }
        val id = harness.startTeaching()
        runBlocking { started.await() }

        runBlocking { engine.abort(id) }

        assertEquals(TranscriptEntry.Interrupted, harness.settled(id).transcript.last())
    }

    @Test
    fun aStopPressedAsTheTurnStartsStopsThatTurn() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        harness.settled(id)

        repeat(20) { attempt ->
            provider.replyWith { awaitCancellation() }
            runBlocking {
                engine.submit(id, LearnerInput.Message("Again $attempt"))
                engine.abort(id)
            }
            val stopped = harness.settled(id)
            assertEquals("attempt $attempt", TranscriptEntry.Interrupted, stopped.transcript.last())
        }
    }

    @Test
    fun abortingAnIdleSessionDoesNothing() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        val before = harness.settled(id)

        runBlocking { engine.abort(id) }

        assertEquals(before, harness.settled(id))
    }

    // ─── steer ──────────────────────────────────────────────────────────────────────────

    /** The next model call streams [partial] and finishes cleanly once [release] completes. */
    private fun pausedReply(partial: String): CompletableDeferred<Unit> {
        val release = CompletableDeferred<Unit>()
        provider.replyWith {
            emit(text(partial))
            release.await()
            emit(ProviderEvent.Done(StopReason.Stop, Usage()))
        }
        return release
    }

    @Test
    fun aSteerQueuesWhileTheModelAnswersAndIsDeliveredAtTheNextBoundaryOfTheSameTurn() {
        val release = pausedReply("Diffie-Hellman works because")
        provider.reply(text("Good question: Eve would need a or b."))
        val id = harness.startTeaching()
        harness.awaitView(id) { v -> v.transcript.any { it == TranscriptEntry.AgentText("Diffie-Hellman works because") } }

        runBlocking { engine.steer(id, "What if Eve changes A?") }
        val queued = harness.awaitView(id) { it.queuedSteers.isNotEmpty() }
        assertEquals(listOf("What if Eve changes A?"), queued.queuedSteers.map { it.text })

        release.complete(Unit)
        val settled = harness.settled(id)

        assertEquals(emptyList<QueuedSteer>(), settled.queuedSteers)
        assertEquals(
            listOf(
                TranscriptEntry.Learner("I want to understand derivatives"),
                TranscriptEntry.AgentText("Diffie-Hellman works because"),
                TranscriptEntry.Learner("What if Eve changes A?"),
                TranscriptEntry.AgentText("Good question: Eve would need a or b."),
            ),
            settled.transcript,
        )
        assertEquals("one Turn, two model calls", 2, provider.requests.size)
        assertEquals("What if Eve changes A?", (provider.requests[1].messages.last() as UserMessage).content)
    }

    private val slopeOptions = listOf("The slope" to "slope", "The area" to "area")

    private fun probe(id: String, question: String = "What does a derivative measure?") =
        toolCall(id, "probe", probeArguments(question, slopeOptions, correct = "slope"))

    private fun answerSlope(sessionId: String, toolCallId: String) = runBlocking {
        engine.submit(sessionId, LearnerInput.Answer(toolCallId, AnswerRequest(values = listOf("slope"), confidence = 70)))
    }

    @Test
    fun aSteerSentWhileTheTurnPosesAQuestionWaitsForTheAnswerAndRidesItsFirstModelCall() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        provider.replyWith {
            started.complete(Unit)
            release.await()
            emit(probe("toolu_1"))
            emit(ProviderEvent.Done(StopReason.ToolUse, Usage()))
        }
        val id = harness.startTeaching()
        runBlocking { started.await() }
        runBlocking { engine.steer(id, "Keep it short, please") }
        release.complete(Unit)

        val asking = harness.settled(id)
        assertEquals("the question ends the Turn without delivering the steer", 1, provider.requests.size)
        assertEquals(listOf("Keep it short, please"), asking.queuedSteers.map { it.text })
        assertEquals("toolu_1", asking.pendingQuestion?.toolCallId)

        provider.reply(text("Right."))
        answerSlope(id, "toolu_1")
        val answered = harness.settled(id)

        assertEquals(emptyList<QueuedSteer>(), answered.queuedSteers)
        assertEquals(2, provider.requests.size)
        val sent = provider.requests[1].messages
        assertTrue("the answer stays next to its question", sent[sent.size - 2] is ToolResultMessage)
        assertEquals("Keep it short, please", (sent.last() as UserMessage).content)
    }

    @Test
    fun aQueuedSteerSurvivesTheProcessAndIsDeliveredExactlyOnce() {
        val started = CompletableDeferred<Unit>()
        provider.replyWith {
            emit(probe("toolu_1"))
            started.complete(Unit)
            awaitCancellation()
        }
        val id = harness.startTeaching()
        runBlocking { started.await() }
        runBlocking { engine.steer(id, "Use a geometric picture") }
        harness.stopEngine()

        val reopened = EngineHarness(provider = provider, database = harness.database)
        assertEquals(listOf("Use a geometric picture"), reopened.eventually(id) { !it.turnRunning }.queuedSteers.map { it.text })

        provider.reply(text("Picture a tangent line."))
        runBlocking { reopened.engine.submit(id, LearnerInput.Message("Go on")) }
        val settled = reopened.settled(id)

        assertEquals(emptyList<QueuedSteer>(), settled.queuedSteers)
        assertEquals(1, settled.transcript.count { it == TranscriptEntry.Learner("Use a geometric picture") })
        assertEquals(1, provider.requests.last().messages.count { (it as? UserMessage)?.content == "Use a geometric picture" })

        provider.reply(text("Next."))
        runBlocking { reopened.engine.submit(id, LearnerInput.Message("And then?")) }
        reopened.settled(id)
        assertEquals(
            "a delivered steer is never delivered again",
            1,
            provider.requests.last().messages.count { (it as? UserMessage)?.content == "Use a geometric picture" },
        )
    }

    @Test
    fun aSteerWithNoTurnRunningAndNoQuestionIsSentLikeAMessage() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        harness.settled(id)

        provider.reply(text("Sure, from the start."))
        runBlocking { engine.steer(id, "Start from the definition") }
        val settled = harness.awaitView(id) { !it.turnRunning && it.transcript.last() == TranscriptEntry.AgentText("Sure, from the start.") }

        assertEquals(TranscriptEntry.Learner("Start from the definition"), settled.transcript[settled.transcript.size - 2])
        assertEquals(emptyList<QueuedSteer>(), settled.queuedSteers)
    }

    @Test
    fun aSteerIsDeliveredAfterToolResultsBeforeTheNextModelCall() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        provider.replyWith {
            started.complete(Unit)
            release.await()
            emit(toolCall("toolu_done", "probe_done", probeDoneArguments("slope" to "solid")))
            emit(ProviderEvent.Done(StopReason.ToolUse, Usage()))
        }
        provider.reply(text("You have the slope idea."))
        provider.reply(text("Let me plan."))
        val id = harness.startTeaching()
        runBlocking { started.await() }
        runBlocking { engine.steer(id, "I already know limits") }
        release.complete(Unit)
        harness.settled(id)

        val second = provider.requests[1].messages
        assertTrue("the result stays next to its call", second[second.size - 2] is ToolResultMessage)
        assertEquals("I already know limits", (second.last() as UserMessage).content)
    }

    // ─── retry ──────────────────────────────────────────────────────────────────────────

    @Test
    fun aFailedResponseIsRetriedOnlyWhenTheLearnerAsksAndTheFailedAttemptStaysSavedButUnsent() {
        provider.replyWith { emit(text("Derivatives are")); throw ProviderException("Overloaded", status = 529) }
        val id = harness.startTeaching()

        val failed = harness.settled(id)
        assertEquals(FailureView("Overloaded", retryable = true), failed.failure)
        assertEquals("no automatic retry", 1, provider.requests.size)

        provider.reply(text("Derivatives measure change."))
        runBlocking { engine.retry(id) }
        val retried = harness.settled(id)

        assertNull(retried.failure)
        assertEquals(2, provider.requests.size)
        assertEquals(
            "the retry starts from the learner's input, without the failed attempt",
            listOf("I want to understand derivatives"),
            provider.requests[1].messages.map { (it as UserMessage).content },
        )
        assertEquals(
            "the failed attempt's partial output stays in the Session",
            listOf(
                TranscriptEntry.Learner("I want to understand derivatives"),
                TranscriptEntry.AgentText("Derivatives are"),
                TranscriptEntry.AgentText("Derivatives measure change."),
            ),
            retried.transcript,
        )
    }

    @Test
    fun aSessionThatDidNotFailCannotBeRetried() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        harness.settled(id)

        try {
            runBlocking { engine.retry(id) }
            fail("expected the retry to be refused")
        } catch (_: RetryNotAllowedException) {
        }
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun anAbortedTurnIsNotOfferedARetry() {
        val started = hangAfter("Half")
        val id = harness.startTeaching()
        runBlocking { started.await() }
        runBlocking { engine.abort(id) }

        assertNull(harness.settled(id).failure)
        try {
            runBlocking { engine.retry(id) }
            fail("expected the retry to be refused")
        } catch (_: RetryNotAllowedException) {
        }
    }

    // ─── sibling questions ──────────────────────────────────────────────────────────────

    @Test
    fun siblingQuestionsAreAnsweredOneAfterAnotherAndOnlyTheLastAnswerRunsATurn() {
        provider.reply(
            probe("toolu_1", "What does a derivative measure?"),
            probe("toolu_2", "What is the derivative of a constant?"),
            probe("toolu_3", "What is the slope of a horizontal line?"),
        )
        val id = harness.startTeaching()
        val first = harness.settled(id).pendingQuestion!!
        assertEquals("toolu_1" to (1 to 3), first.toolCallId to (first.position to first.siblings))

        answerSlope(id, "toolu_1")
        val second = harness.awaitView(id) { it.pendingQuestion?.toolCallId == "toolu_2" }
        assertEquals(2 to 3, second.pendingQuestion!!.position to second.pendingQuestion!!.siblings)
        assertEquals("the answer shows at once", 1, second.transcript.count { it is TranscriptEntry.Answered })
        answerSlope(id, "toolu_2")
        harness.awaitView(id) { it.pendingQuestion?.toolCallId == "toolu_3" }
        assertEquals("no model call until the last answer", 1, provider.requests.size)

        provider.reply(text("Three for three on the slope."))
        answerSlope(id, "toolu_3")
        val settled = harness.awaitView(id) { !it.turnRunning && it.transcript.last() == TranscriptEntry.AgentText("Three for three on the slope.") }

        assertNull(settled.pendingQuestion)
        assertEquals(2, provider.requests.size)
        val sent = provider.requests[1].messages
        assertEquals(
            "every answer sits right after the message that asked",
            listOf("toolu_1", "toolu_2", "toolu_3"),
            sent.takeLast(3).map { (it as ToolResultMessage).toolCallId },
        )
    }

    // ─── drafts ─────────────────────────────────────────────────────────────────────────

    @Test
    fun answerAndComposerDraftsSurviveTheProcess() {
        provider.reply(probe("toolu_1"))
        val id = harness.startTeaching()
        harness.settled(id)
        val draft = AnswerDraft(values = listOf("slope"), confidence = 70, thinking = "It is about rates")
        runBlocking {
            engine.saveAnswerDraft(id, "toolu_1", draft)
            engine.saveComposerDraft(id, "Could you also show")
        }
        harness.stopEngine()

        val reopened = EngineHarness(provider = provider, database = harness.database)
        val restored = runBlocking { reopened.engine.drafts(id) }

        assertEquals(SessionDrafts(composer = "Could you also show", answers = mapOf("toolu_1" to draft)), restored)
    }

    @Test
    fun answeringAQuestionOrSendingAMessageClearsItsDraft() {
        provider.reply(probe("toolu_1"))
        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { engine.saveAnswerDraft(id, "toolu_1", AnswerDraft(answer = "", thinking = "Rates")) }

        provider.reply(text("Right."))
        answerSlope(id, "toolu_1")
        harness.settled(id)
        assertEquals(SessionDrafts(), runBlocking { engine.drafts(id) })

        runBlocking { engine.saveComposerDraft(id, "Next topic") }
        provider.reply(text("Okay."))
        runBlocking { engine.submit(id, LearnerInput.Message("Next topic")) }
        harness.settled(id)
        assertEquals(SessionDrafts(), runBlocking { engine.drafts(id) })
    }

    @Test
    fun anEmptyDraftIsForgotten() {
        provider.reply(text("Hello."))
        val id = harness.startTeaching()
        harness.settled(id)

        runBlocking {
            engine.saveComposerDraft(id, "Half a thought")
            engine.saveComposerDraft(id, "")
        }

        assertEquals(SessionDrafts(), runBlocking { engine.drafts(id) })
    }
}
