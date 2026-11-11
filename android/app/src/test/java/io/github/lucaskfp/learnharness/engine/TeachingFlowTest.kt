package io.github.lucaskfp.learnharness.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lucaskfp.learnharness.engine.model.ToolResultMessage
import io.github.lucaskfp.learnharness.engine.plan.PlanEdge
import io.github.lucaskfp.learnharness.engine.plan.PlanNode
import io.github.lucaskfp.learnharness.engine.question.AnswerRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Teaching Session from Probe to Plan to Teach, through the engine's public interface. Only
 * the model is scripted; handoff, Plan validation and node-derived position are the real engine.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TeachingFlowTest {
    private val harness = EngineHarness()
    private val provider = harness.provider

    @After
    fun tearDown() = harness.close()

    private val assessment = probeDoneArguments("the derivative as a limit" to "Knows slopes, not limits.")
    private val slopeOptions = listOf("Rise over run" to "a", "Area under the curve" to "b")

    private fun toolNames(requestIndex: Int) = provider.requests[requestIndex].tools.map { it.name }.toSet()

    private fun resultsSentToTheModel(requestIndex: Int) =
        provider.requests[requestIndex].messages.filterIsInstance<ToolResultMessage>()

    /** The Prober concludes (two model calls); the Teacher's script follows. */
    private fun scriptHandoff() {
        provider.reply(text("Thanks, I have what I need."), toolCall("pd_1", "probe_done", assessment))
        provider.reply(text("Over to the plan."))
    }

    /** Handoff, then the Teacher presents a two-node Plan (model calls 3 and 4). */
    private fun teachWithPlan() {
        scriptHandoff()
        provider.reply(toolCall("plan_1", "plan_init", planInitArguments(TwoNodePlan)))
        provider.reply(text("Here is the plan."))
    }

    private fun answer(sessionId: String, toolCallId: String, vararg values: String) = runBlocking {
        harness.engine.submit(sessionId, LearnerInput.Answer(toolCallId, AnswerRequest(values = values.toList(), confidence = 90)))
    }

    // ─── handoff ────────────────────────────────────────────────────────────────────────

    @Test
    fun aSuccessfulProbeDoneHandsOffToTheTeacherWhoPlansWithoutAnotherLearnerMessage() {
        provider.reply(text("Thanks, I have what I need."), toolCall("pd_1", "probe_done", assessment))
        provider.reply(text("Over to the plan."))
        provider.reply(toolCall("plan_1", "plan_init", planInitArguments(TwoNodePlan)))
        provider.reply(text("Here is the plan."))

        val id = harness.startTeaching()
        val view = harness.awaitView(id) { !it.turnRunning && it.phase == SessionPhase.Planning }

        // The Prober gets one more call for its closing sentence, then the Teacher takes over.
        assertEquals(4, provider.requests.size)
        assertTrue("probe_done" in toolNames(0))
        assertTrue("probe_done" in toolNames(1))
        assertTrue("plan_init" in toolNames(2))
        assertFalse("probe_done" in toolNames(2))
        assertFalse("probe" in toolNames(2))
        assertNotEquals(provider.requests[1].systemPrompt, provider.requests[2].systemPrompt)
        assertNull(view.pendingQuestion)
    }

    @Test
    fun probeDoneIsRefusedWhileAQuestionIsOpenAndNothingIsHandedOff() {
        provider.reply(
            toolCall("q_1", "probe", probeArguments("What is a slope?", slopeOptions, correct = "a")),
            toolCall("pd_1", "probe_done", assessment),
        )

        val id = harness.startTeaching()
        val view = harness.settled(id)

        assertEquals(SessionPhase.Probing, view.phase)
        assertEquals("q_1", view.pendingQuestion!!.toolCallId)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun aProbeDoneThatFailsValidationDoesNotHandOffAndTheProberIsToldWhatToFix() {
        val noStrands = buildJsonObject { put("assessment", buildJsonObject { put("strands", JsonArray(emptyList())) }) }
        provider.reply(toolCall("pd_bad", "probe_done", noStrands))
        provider.reply(toolCall("pd_good", "probe_done", assessment))
        provider.reply(text("Done."))
        provider.reply(text("Planning now."))

        val id = harness.startTeaching()
        val view = harness.awaitView(id) { !it.turnRunning && it.phase == SessionPhase.Planning }

        val firstResult = resultsSentToTheModel(1).single()
        assertTrue(firstResult.isError)
        assertTrue(firstResult.content.single().text, "strands" in firstResult.content.single().text)
        assertTrue("probe_done" in toolNames(1) && "plan_init" !in toolNames(1))
        assertEquals(SessionPhase.Planning, view.phase)
    }

    // ─── the Plan ───────────────────────────────────────────────────────────────────────

    @Test
    fun theTeachersPlanInitBuildsTheDagAndTheLearnerSeesIt() {
        teachWithPlan()

        val id = harness.startTeaching()
        val view = harness.awaitView(id) { !it.turnRunning && it.plan != null }

        val plan = view.plan!!
        assertEquals(listOf(PlanNode("slope", "The slope idea"), PlanNode("rule", "The power rule")), plan.nodes)
        assertEquals(listOf(PlanEdge("slope", "rule")), plan.edges)
        assertNull(plan.current)
        assertTrue(plan.visited.isEmpty())
        assertEquals(SessionPhase.Planning, view.phase)
        val boundary = view.transcript.filterIsInstance<TranscriptEntry.PhaseBoundary>().single()
        assertEquals(SessionPhase.Planning, boundary.phase)
        assertEquals(2, boundary.nodeCount)
        assertEquals(plan, view.transcript.filterIsInstance<TranscriptEntry.PlanSnapshot>().single().plan)
    }

    @Test
    fun theModelIsToldTheBindingNodeIdsAndNotHandedTheTreeBack() {
        teachWithPlan()
        provider.reply(text("Anything else?"))

        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Looks good")) }
        harness.settled(id)

        val ack = resultsSentToTheModel(provider.requests.lastIndex).single { it.toolName == "plan_init" }.content.single().text
        val compact = ack.replace(" ", "")
        assertTrue(ack, "\"nodeIds\":[\"slope\",\"rule\"]" in compact)
        assertTrue(ack, "only valid nodeId values" in ack)
        assertFalse(ack, "\"edges\"" in compact)
    }

    @Test
    fun aPlanThatIsNotADagReturnsCorrectableErrorsAndTheTeacherRetries() {
        scriptHandoff()
        provider.reply(toolCall("plan_bad", "plan_init", planInitArguments("graph TD\n  a --> b --> c --> a")))
        provider.reply(toolCall("plan_bad2", "plan_init", planInitArguments("graph TD\n  Bad Id --> b")))
        provider.reply(toolCall("plan_ok", "plan_init", planInitArguments(TwoNodePlan)))
        provider.reply(text("Here is the plan."))

        val id = harness.startTeaching()
        val view = harness.awaitView(id) { !it.turnRunning && it.plan != null }

        val cycle = resultsSentToTheModel(3).single { it.toolCallId == "plan_bad" }
        assertTrue(cycle.content.single().text, "would create a cycle" in cycle.content.single().text)
        val syntax = resultsSentToTheModel(4).single { it.toolCallId == "plan_bad2" }
        assertTrue(syntax.content.single().text, "line 2" in syntax.content.single().text)
        assertEquals(2, view.plan!!.nodes.size)
        assertEquals(1, view.transcript.filterIsInstance<TranscriptEntry.PlanSnapshot>().size)
    }

    @Test
    fun callingPlanInitAgainReplacesThePlanAndTheLearnerSeesTheNewOne() {
        teachWithPlan()
        provider.reply(toolCall("plan_2", "plan_init", planInitArguments("graph TD\n  slope --> rule --> chain")))
        provider.reply(text("Revised."))

        val id = harness.startTeaching()
        harness.awaitView(id) { !it.turnRunning && it.plan != null }
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Please add the chain rule")) }
        val view = harness.awaitView(id) { !it.turnRunning && it.plan?.nodes?.size == 3 }

        assertEquals(listOf("slope", "rule", "chain"), view.plan!!.nodes.map { it.id })
        val snapshots = view.transcript.filterIsInstance<TranscriptEntry.PlanSnapshot>()
        assertEquals(listOf(2, 3), snapshots.map { it.plan.nodes.size })
        assertEquals(
            "only the first Plan opens the Planning seam",
            1,
            view.transcript.filterIsInstance<TranscriptEntry.PhaseBoundary>().size,
        )
    }

    // ─── teaching ───────────────────────────────────────────────────────────────────────

    @Test
    fun theFirstQuizTaggedWithAPlanNodeStartsTeachingAndMovesThePosition() {
        teachWithPlan()
        provider.reply(toolCall("quiz_1", "quiz", quizArguments("slope", "What is a slope?", slopeOptions, correct = "a")))
        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Looks good, start")) }
        val view = harness.awaitView(id) { it.pendingQuestion != null }

        assertEquals(SessionPhase.Teaching, view.phase)
        assertEquals("slope", view.plan!!.current)
        assertTrue(view.plan!!.visited.isEmpty())
        assertEquals(SessionPhase.Teaching, view.transcript.filterIsInstance<TranscriptEntry.PhaseBoundary>().last().phase)
    }

    private fun teachingSession(): String {
        teachWithPlan()
        provider.reply(toolCall("quiz_1", "quiz", quizArguments("slope", "What is a slope?", slopeOptions, correct = "a")))
        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Looks good, start")) }
        harness.awaitView(id) { it.pendingQuestion?.toolCallId == "quiz_1" }
        return id
    }

    @Test
    fun positionFollowsTheTeachersTaggedCallsEvenWhenTheLearnerAnswersWrong() {
        val id = teachingSession()
        provider.reply(toolCall("quiz_2", "quiz", quizArguments("rule", "Derivative of x squared?", listOf("2x" to "a", "x" to "b"), correct = "a")))

        answer(id, "quiz_1", "b") // wrong
        val view = harness.awaitView(id) { it.pendingQuestion?.toolCallId == "quiz_2" }

        val graded = view.transcript.filterIsInstance<TranscriptEntry.Answered>().single().data
        assertEquals(false, graded.correct)
        assertEquals("rule", view.plan!!.current)
        assertEquals(setOf("slope"), view.plan!!.visited)
    }

    @Test
    fun aQuizWithANodeIdOutsideThePlanReturnsToTheTeacherWithTheValidIds() {
        teachWithPlan()
        provider.reply(toolCall("quiz_bad", "quiz", quizArguments("limits", "What is a limit?", slopeOptions, correct = "a")))
        provider.reply(toolCall("quiz_ok", "quiz", quizArguments("rule", "Derivative of x squared?", listOf("2x" to "a", "x" to "b"), correct = "a")))

        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Start")) }
        val view = harness.awaitView(id) { it.pendingQuestion != null }

        val rejection = resultsSentToTheModel(5).single { it.toolCallId == "quiz_bad" }
        assertTrue(rejection.isError)
        val text = rejection.content.single().text
        assertTrue(text, "nodeId \"limits\" matches no node" in text && "slope, rule" in text)
        assertEquals("quiz_ok", view.pendingQuestion!!.toolCallId)
        assertEquals("rule", view.plan!!.current)
        assertEquals("a rejected call moved nothing", emptySet<String>(), view.plan!!.visited)
    }

    @Test
    fun aQuizBeforeAnyPlanExistsIsRejectedUntilPlanInitIsCalled() {
        scriptHandoff()
        provider.reply(toolCall("quiz_early", "quiz", quizArguments("slope", "What is a slope?", slopeOptions, correct = "a")))
        provider.reply(text("Let me plan first."))

        val id = harness.startTeaching()
        val view = harness.settled(id)

        val rejection = resultsSentToTheModel(3).single { it.toolCallId == "quiz_early" }
        assertTrue(rejection.content.single().text, "call plan_init before posing quizzes" in rejection.content.single().text)
        assertNull(view.pendingQuestion)
        assertNull(view.plan)
    }

    @Test
    fun aTeacherQuizIsGradedOnTheDeviceAndTheModelSeesTheResult() {
        val id = teachingSession()
        provider.reply(text("Right."))

        answer(id, "quiz_1", "a")
        val view = harness.settled(id)

        val data = view.transcript.filterIsInstance<TranscriptEntry.Answered>().single().data
        assertEquals(true, data.correct)
        assertEquals(90, data.confidence)
        val seen = resultsSentToTheModel(provider.requests.lastIndex).single { it.toolCallId == "quiz_1" }.content.single().text
        assertTrue(seen, "\"correct\":true" in seen.replace(" ", ""))
    }

    @Test
    fun theTeachersQuizOptionsSentAsAJsonStringAreHealedBeforeValidation() {
        teachWithPlan()
        val args = quizArguments("slope", "What is a slope?", slopeOptions, correct = "a")
        provider.reply(
            toolCall(
                "quiz_1", "quiz",
                buildJsonObject {
                    args.forEach { (key, value) -> put(key, if (key == "options") JsonPrimitive(value.toString()) else value) }
                },
            ),
        )

        val id = harness.startTeaching()
        harness.settled(id)
        runBlocking { harness.engine.submit(id, LearnerInput.Message("Start")) }
        val view = harness.awaitView(id) { it.pendingQuestion != null }

        assertEquals(setOf("a", "b"), view.pendingQuestion!!.options.map { it.value }.toSet())
    }
}
