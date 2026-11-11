package io.github.lucaskfp.learnharness.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.model.AssistantMessage
import io.github.lucaskfp.learnharness.engine.model.ThinkingContent
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.Usage
import io.github.lucaskfp.learnharness.engine.provider.ProviderEvent
import io.github.lucaskfp.learnharness.engine.prompts.TeachingLanguage
import io.github.lucaskfp.learnharness.engine.provider.ProviderException
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import io.github.lucaskfp.learnharness.engine.question.QuestionKind
import io.github.lucaskfp.learnharness.engine.tools.QuizMechanics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Session engine through its public interface only: start, submit, observe. Only the model is
 * scripted; the engine, upstream's prompts and tool contracts, and Room are real.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SessionEngineTest {
    private val harness = EngineHarness()
    private val provider = harness.provider
    private val engine = harness.engine

    @After
    fun tearDown() = harness.close()

    private val derivativeOptions = listOf(
        "The slope of the tangent line" to "slope",
        "The area under the curve" to "area",
        "The average value" to "average",
        "The maximum" to "max",
    )

    private fun probeDerivative() = toolCall(
        "toolu_1", "probe",
        probeArguments("What does a derivative measure?", derivativeOptions, correct = "slope"),
    )

    @Test
    fun startingATeachingSessionRunsATurnAndTheProbersQuestionWaitsForTheLearner() {
        provider.reply(text("Let's find where you are first."), probeDerivative())

        val id = harness.startTeaching("I want to understand derivatives")
        val view = harness.settled(id)

        assertEquals("I want to understand derivatives", view.goal)
        assertEquals(SessionKind.Teaching, view.kind)
        assertEquals(SessionPhase.Probing, view.phase)
        assertEquals(
            listOf(
                TranscriptEntry.Learner("I want to understand derivatives"),
                TranscriptEntry.AgentText("Let's find where you are first."),
            ),
            view.transcript,
        )
        val question = view.pendingQuestion!!
        assertEquals(QuestionKind.Quiz, question.kind)
        assertEquals("What does a derivative measure?", question.text)
        assertEquals(derivativeOptions.map { it.second }.toSet(), question.options.map { it.value }.toSet())
        assertFalse(question.freeText)
    }

    @Test
    fun theLearnersGoalIsSavedBeforeTheModelAnswers() {
        val release = CompletableDeferred<Unit>()
        provider.replyWith {
            release.await()
            emit(text("Hello"))
            emit(ProviderEvent.Done("stop", Usage()))
        }

        val id = harness.startTeaching("Teach me recursion")
        val midTurn = harness.awaitView(id) { it.turnRunning && it.transcript.isNotEmpty() }

        assertEquals(listOf<TranscriptEntry>(TranscriptEntry.Learner("Teach me recursion")), midTurn.transcript)
        release.complete(Unit)
        harness.settled(id)
    }

    @Test
    fun outputStreamsIntoTheViewWhileTheTurnRuns() {
        val release = CompletableDeferred<Unit>()
        provider.replyWith {
            emit(text("Thinking out "))
            emit(text("loud"))
            release.await()
            emit(ProviderEvent.Done("stop", Usage()))
        }

        val id = harness.startTeaching()
        val streaming = harness.awaitView(id) { v -> v.transcript.any { it == TranscriptEntry.AgentText("Thinking out loud") } }

        assertTrue(streaming.turnRunning)
        release.complete(Unit)
        val settled = harness.settled(id)
        assertEquals(TranscriptEntry.AgentText("Thinking out loud"), settled.transcript.last())
    }

    @Test
    fun answeringWithConfidenceAndThinkingGradesOnTheDeviceAndRunsTheNextTurn() {
        provider.reply(probeDerivative())
        provider.reply(text("Good, you have the slope idea. Next: limits."))
        val id = harness.startTeaching()
        val question = harness.settled(id).pendingQuestion!!

        runBlocking {
            engine.submit(
                id,
                LearnerInput.Answer(
                    question.toolCallId,
                    AnswerRequest(values = listOf("slope"), confidence = 70, thinking = "Rate of change at a point"),
                ),
            )
        }
        val view = harness.settled(id)

        assertNull(view.pendingQuestion)
        val answered = view.transcript.filterIsInstance<TranscriptEntry.Answered>().single().data
        assertEquals(true, answered.correct)
        assertEquals(70, answered.confidence)
        assertEquals("Rate of change at a point", answered.thinking)
        assertEquals("slope", answered.answers.single().value)
        assertEquals(TranscriptEntry.AgentText("Good, you have the slope idea. Next: limits."), view.transcript.last())

        // The model reads only what the learner added, as the question call's own result.
        val result = provider.requests.last().messages.filterIsInstance<ToolResultMessage>().single()
        assertEquals("toolu_1", result.toolCallId)
        val seen = JsonObjectOf(result.content.single().text)
        assertEquals("quiz", seen["kind"]!!.jsonPrimitive.content)
        assertEquals("true", seen["correct"]!!.jsonPrimitive.content)
        assertEquals("70", seen["confidence"]!!.jsonPrimitive.content)
        assertEquals("Rate of change at a point", seen["thinking"]!!.jsonPrimitive.content)
        assertFalse("the explanation and options are not echoed back", seen.containsKey("explanation"))
    }

    @Test
    fun aWrongAnswerAndIDontKnowAreBothGradedIncorrect() {
        provider.reply(probeDerivative())
        provider.reply(
            toolCall("toolu_2", "probe", probeArguments("Which is a limit?", listOf("A" to "a", "B" to "b"), correct = "a")),
        )
        provider.reply(text("Noted."))
        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking {
            engine.submit(id, LearnerInput.Answer("toolu_1", AnswerRequest(values = listOf("area"), confidence = 90)))
        }
        val second = harness.settled(id).pendingQuestion!!
        runBlocking { engine.submit(id, LearnerInput.Answer(second.toolCallId, AnswerRequest(dontKnow = true, confidence = 100))) }

        val answers = harness.settled(id).transcript.filterIsInstance<TranscriptEntry.Answered>().map { it.data }

        assertEquals(listOf(false, false), answers.map { it.correct })
        assertEquals("a wrong answer keeps its confidence", 90, answers[0].confidence)
        assertNull("no confidence rides on I don't know", answers[1].confidence)
        val seen = JsonObjectOf(provider.requests.last().messages.filterIsInstance<ToolResultMessage>().last().content.single().text)
        assertEquals("true", seen["dontKnow"]!!.jsonPrimitive.content)
        assertFalse(seen.containsKey("correct"))
    }

    @Test
    fun optionsAreShuffledPerQuestionAndStableAcrossObservations() {
        provider.reply(probeDerivative())
        val id = harness.startTeaching()

        val first = harness.settled(id).pendingQuestion!!.options
        val again = harness.settled(id).pendingQuestion!!.options

        assertEquals(first, again)
        assertEquals(4, first.size)
    }

    @Test
    fun aQuestionThatFailsAuthoringChecksReturnsToTheModelAndTheLearnerNeverSeesIt() {
        provider.reply(
            toolCall("toolu_bad", "probe", probeArguments("Which?", listOf("A" to "a", "B" to "b"), correct = "zzz")),
        )
        provider.reply(
            toolCall("toolu_good", "probe", probeArguments("Which, really?", listOf("A" to "a", "B" to "b"), correct = "a")),
        )
        val id = harness.startTeaching()

        val view = harness.settled(id)

        assertEquals("toolu_good", view.pendingQuestion!!.toolCallId)
        assertEquals(1, view.transcript.size) // only the goal
        val correction = provider.requests[1].messages.filterIsInstance<ToolResultMessage>().single()
        assertTrue(correction.isError)
        assertTrue(correction.content.single().text.contains("correctAnswer \"zzz\" does not match any option value"))
    }

    @Test
    fun aCallThatFailsSchemaValidationReturnsAValidationErrorToTheModel() {
        provider.reply(toolCall("toolu_bad", "probe", buildJsonObject { put("question", "Only a question") }))
        provider.reply(text("Sorry, let me try again."))
        val id = harness.startTeaching()

        val view = harness.settled(id)

        assertNull(view.pendingQuestion)
        val correction = provider.requests[1].messages.filterIsInstance<ToolResultMessage>().single()
        assertTrue(correction.isError)
        assertTrue(correction.content.single().text.startsWith("Validation failed for tool \"probe\":"))
        assertTrue(correction.content.single().text.contains("must have required property 'options'"))
    }

    @Test
    fun optionsSentAsAJsonEncodedStringAreHealed() {
        val encoded = JsonPrimitive("""[{"label":"A","value":"a"},{"label":"B","value":"b"}]""")
        provider.reply(
            toolCall(
                "toolu_1", "probe",
                buildJsonObject {
                    put("question", "Which?")
                    put("options", encoded)
                    put("explanation", "Because.")
                    put("correctAnswer", "a")
                },
            ),
        )
        val id = harness.startTeaching()

        val question = harness.settled(id).pendingQuestion!!

        assertEquals(listOf("a", "b"), question.options.map { it.value }.sorted())
    }

    @Test
    fun askUserQuestionPoseAnUngradedQuestionWithOrWithoutOptions() {
        provider.reply(toolCall("toolu_ask", "ask_user_question", buildJsonObject { put("question", "What have you tried so far?") }))
        provider.reply(
            toolCall(
                "toolu_ask2", "ask_user_question",
                buildJsonObject {
                    put("question", "Which do you prefer?")
                    put("options", optionsJson(listOf("Examples" to "ex", "Theory" to "th")))
                },
            ),
        )
        provider.reply(text("Thanks."))
        val id = harness.startTeaching()
        val free = harness.settled(id).pendingQuestion!!
        assertEquals(QuestionKind.Ask, free.kind)
        assertTrue(free.freeText)

        runBlocking { engine.submit(id, LearnerInput.Answer(free.toolCallId, AnswerRequest(text = "Khan Academy", thinking = "I tried the basics"))) }
        val chosen = harness.settled(id).pendingQuestion!!
        assertFalse(chosen.freeText)
        runBlocking { engine.submit(id, LearnerInput.Answer(chosen.toolCallId, AnswerRequest(values = listOf("th")))) }
        val view = harness.settled(id)

        val answers = view.transcript.filterIsInstance<TranscriptEntry.Answered>().map { it.data }
        assertEquals("Khan Academy", answers[0].text)
        assertNull("an ungraded question has no verdict", answers[0].correct)
        assertEquals("th", answers[1].answers.single().value)
    }

    @Test
    fun theProberCanQuizWithFreeRecallWhenThatMechanicIsOn() {
        val recallHarness = EngineHarness(mechanics = QuizMechanics.FreeRecall)
        try {
            recallHarness.provider.reply(
                toolCall(
                    "toolu_r", "recall",
                    buildJsonObject {
                        put("question", "Explain what a limit is, in your own words.")
                        put("correctAnswer", "The value a function approaches as the input approaches a point.")
                    },
                ),
            )
            recallHarness.provider.reply(text("Close."))
            val id = recallHarness.startTeaching()
            val question = recallHarness.settled(id).pendingQuestion!!

            assertEquals(QuestionKind.Explain, question.kind)
            assertTrue(question.freeText)
            assertEquals(listOf("recall", "ask_user_question", "probe_done"), recallHarness.provider.requests.first().tools.map { it.name })

            runBlocking {
                recallHarness.engine.submit(id, LearnerInput.Answer("toolu_r", AnswerRequest(text = "What it approaches")))
            }
            val answered = recallHarness.settled(id).transcript.filterIsInstance<TranscriptEntry.Answered>().single().data
            assertEquals("What it approaches", answered.text)
            assertEquals("The value a function approaches as the input approaches a point.", answered.committed)
        } finally {
            recallHarness.close()
        }
    }

    @Test
    fun theModelIsSentUpstreamsProberPromptAndToolContractsInTheTeachingLanguage() {
        provider.reply(text("Olá!"))

        val id = harness.startTeaching(language = TeachingLanguage.PortugueseBrazil)
        harness.settled(id)

        val request = provider.requests.single()
        assertEquals("claude-sonnet-5-5", request.model)
        assertEquals(listOf("probe", "ask_user_question", "probe_done"), request.tools.map { it.name })
        assertTrue(request.tools.first { it.name == "probe" }.description.startsWith("Ask the learner a GRADED question"))
        assertTrue(request.systemPrompt.startsWith("# The agent"))
        assertTrue("the prober doctrine is composed in", request.systemPrompt.contains("probe"))
        assertTrue(request.systemPrompt.trimEnd().endsWith("exactly as their schemas specify."))
        assertTrue(request.systemPrompt.contains("Brazilian Portuguese"))
        assertTrue(request.thinking)
    }

    @Test
    fun aSecondInputWhileATurnRunsIsRejected() {
        val release = CompletableDeferred<Unit>()
        provider.replyWith { release.await(); emit(ProviderEvent.Done("stop", Usage())) }
        val id = harness.startTeaching()

        try {
            runBlocking { engine.submit(id, LearnerInput.Message("Hello?")) }
            fail("expected a Turn to be in progress")
        } catch (_: TurnInProgressException) {
        }
        release.complete(Unit)
        harness.settled(id)
    }

    @Test
    fun aFreeMessageStartsAnotherTurnWhenNoQuestionIsPending() {
        provider.reply(text("What would you like to start with?"))
        provider.reply(text("Great, derivatives it is."))
        val id = harness.startTeaching()
        harness.settled(id)

        runBlocking { engine.submit(id, LearnerInput.Message("Derivatives please")) }
        val view = harness.settled(id)

        assertEquals(TranscriptEntry.Learner("Derivatives please"), view.transcript[view.transcript.size - 2])
        assertEquals(TranscriptEntry.AgentText("Great, derivatives it is."), view.transcript.last())
    }

    @Test
    fun answeringAQuestionThatIsNotPendingIsRejected() {
        provider.reply(text("No question this time."))
        val id = harness.startTeaching()
        harness.settled(id)

        try {
            runBlocking { engine.submit(id, LearnerInput.Answer("toolu_nope", AnswerRequest(values = listOf("a")))) }
            fail("expected no pending question")
        } catch (_: NoPendingQuestionException) {
        }
        assertFalse(harness.settled(id).turnRunning)
    }

    @Test
    fun aFailedModelCallIsRecordedAndTheLearnerCanKeepGoing() {
        provider.replyWith { throw ProviderException("Overloaded", status = 529) }
        provider.reply(text("Back again."))
        val id = harness.startTeaching()

        val failed = harness.settled(id)
        assertEquals("Overloaded", failed.error)

        runBlocking { engine.submit(id, LearnerInput.Message("Let's continue")) }
        val recovered = harness.settled(id)

        assertNull(recovered.error)
        assertEquals(TranscriptEntry.AgentText("Back again."), recovered.transcript.last())
        // The failed generation is not replayed to the model.
        assertTrue(provider.requests.last().messages.none { it is AssistantMessage })
    }

    @Test
    fun reasoningIsSavedWithItsSignatureAndReplayedToTheModel() {
        provider.reply(*thinking("The learner wants calculus.", signature = "sig-1").toTypedArray(), probeDerivative())
        provider.reply(text("Fine."))
        val id = harness.startTeaching()
        val view = harness.settled(id)
        assertTrue(view.transcript.contains(TranscriptEntry.Reasoning("The learner wants calculus.")))

        runBlocking { engine.submit(id, LearnerInput.Answer("toolu_1", AnswerRequest(values = listOf("slope"), confidence = 50))) }
        harness.settled(id)

        val replayed = provider.requests.last().messages.filterIsInstance<AssistantMessage>().single()
            .content.filterIsInstance<ThinkingContent>().single()
        assertEquals("sig-1", replayed.thinkingSignature)
    }

    @Test
    fun aSessionResumesMidQuestionAfterTheAppRestarts() {
        provider.reply(probeDerivative())
        val id = harness.startTeaching()
        harness.settled(id)

        val restarted = EngineHarness(provider = provider, database = harness.database)
        val question = restarted.eventually(id) { !it.turnRunning }.pendingQuestion

        assertNotNull(question)
        assertEquals("What does a derivative measure?", question!!.text)
    }

    @Test
    fun cancellingATurnKeepsWhatWasGeneratedAndDropsItFromTheNextModelCall() {
        val started = CompletableDeferred<Unit>()
        provider.replyWith {
            emit(text("Half a sentence"))
            started.complete(Unit)
            awaitCancellation()
        }
        val id = harness.startTeaching()
        runBlocking { started.await() }

        harness.stopEngine()

        // A new engine over the same database sees the partial output, saved at settlement.
        val reopened = EngineHarness(provider = provider, database = harness.database)
        val view = reopened.eventually(id) { v -> v.transcript.any { it == TranscriptEntry.AgentText("Half a sentence") } }
        assertTrue(view.transcript.contains(TranscriptEntry.AgentText("Half a sentence")))
    }

    @Test
    fun sessionsListShowsTheMostRecentFirstAndWhetherATurnIsRunning() {
        provider.reply(text("First."))
        val release = CompletableDeferred<Unit>()
        provider.replyWith { release.await(); emit(ProviderEvent.Done("stop", Usage())) }
        val first = harness.startTeaching("A very long goal ".repeat(10))
        harness.settled(first)
        val second = harness.startTeaching("Second goal")

        val list = runBlocking {
            withTimeout(10_000) { engine.sessions().first { it.size == 2 } }
        }

        assertEquals(listOf(second, first), list.map { it.id })
        assertTrue(list[0].turnRunning)
        assertFalse(list[1].turnRunning)
        assertTrue("the title is cut at a word boundary", list[1].goal.endsWith("…") && list[1].goal.length <= 73)
        release.complete(Unit)
        harness.settled(second)
    }

    @Test
    fun deletingASessionRemovesItAndItsHistory() {
        provider.reply(text("Hi."))
        val id = harness.startTeaching()
        harness.settled(id)

        runBlocking { engine.delete(id) }

        assertNull(runBlocking { withTimeout(10_000) { engine.observe(id).first { true } } })
        assertTrue(runBlocking { engine.sessions().first() }.isEmpty())
    }

    private fun JsonObjectOf(text: String) = LhJson.parseToJsonElement(text).jsonObject
}
