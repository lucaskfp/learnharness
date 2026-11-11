package io.github.lucaskfp.learnharness.engine.tools

import io.github.lucaskfp.learnharness.engine.hasProbeDone
import io.github.lucaskfp.learnharness.engine.hasUnresolvedQuestion
import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.model.LhMessage
import io.github.lucaskfp.learnharness.engine.plan.PlanParse
import io.github.lucaskfp.learnharness.engine.plan.PlanTree
import io.github.lucaskfp.learnharness.engine.plan.PlanTreeError
import io.github.lucaskfp.learnharness.engine.plan.createTree
import io.github.lucaskfp.learnharness.engine.plan.latestPlanTree
import io.github.lucaskfp.learnharness.engine.plan.parseMermaidPlan
import io.github.lucaskfp.learnharness.engine.model.ToolCall
import io.github.lucaskfp.learnharness.engine.question.QuestionTools
import io.github.lucaskfp.learnharness.engine.question.askOptionProblems
import io.github.lucaskfp.learnharness.engine.question.explainInputProblems
import io.github.lucaskfp.learnharness.engine.question.quizInputProblems
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The two agents of a Teaching Session. */
enum class AgentKind { Prober, Teacher }

/** How the agents quiz: graded multiple choice (`probe`, `quiz`) or free recall (`recall`, `explain`). */
enum class QuizMechanics { Choice, FreeRecall }

/** What running a tool call produced. */
data class ToolOutcome(
    /** The text the model reads. */
    val text: String,
    val isError: Boolean,
    /** The record-facing value kept with the result. */
    val details: JsonElement? = null,
    /** A posed question: the Turn ends and the learner's answer becomes this call's result. */
    val terminates: Boolean = false,
)

/**
 * The tools of the Teaching Session's two agents, with upstream's argument healing before
 * validation and its authoring refinements after. A question that passes ends the Turn; a call
 * that fails returns to the model as a correctable error.
 *
 * The Prober has `probe` or `recall` (by mechanics), `ask_user_question` and `probe_done`; the
 * Teacher has `quiz` or `explain`, `ask_user_question` and `plan_init`.
 */
class AgentTools(private val contracts: ToolContracts, mechanics: QuizMechanics = QuizMechanics.Choice) {
    private val prober = listOf(
        contracts.spec(if (mechanics == QuizMechanics.Choice) "probe" else "recall"),
        contracts.spec("ask_user_question"),
        contracts.spec("probe_done"),
    )
    private val teacher = listOf(
        contracts.spec(if (mechanics == QuizMechanics.Choice) "quiz" else "explain"),
        contracts.spec("ask_user_question"),
        contracts.spec("plan_init"),
    )

    fun specs(kind: AgentKind): List<ToolSpec> = if (kind == AgentKind.Prober) prober else teacher

    /** Runs one call against [history], which already holds the assistant message that made it. */
    fun execute(kind: AgentKind, call: ToolCall, history: List<LhMessage>): ToolOutcome {
        val spec = specs(kind).firstOrNull { it.name == call.name }
            ?: return ToolOutcome("Tool ${call.name} not found", isError = true)
        call.argumentsError?.let {
            return ToolOutcome("Invalid arguments for tool \"${call.name}\": $it", isError = true)
        }
        val healed = ArgumentHeal.prepare(call.name, call.arguments, spec.inputSchema)
        val arguments = SchemaValidator.normalize(spec.inputSchema, healed) as JsonObject
        val problems = SchemaValidator.validate(spec.inputSchema, arguments)
        if (problems.isNotEmpty()) {
            return ToolOutcome(SchemaValidator.errorText(call.name, problems, call.arguments), isError = true)
        }
        return when (call.name) {
            "probe_done" -> probeDone(history)
            "plan_init" -> planInit(arguments)
            else -> question(call.name, arguments, history)
        }
    }

    private fun question(tool: String, arguments: JsonObject, history: List<LhMessage>): ToolOutcome {
        val authoring = when (tool) {
            "probe" -> quizInputProblems(arguments)
            "recall" -> explainInputProblems(arguments)
            // The Teacher's questions name the Plan node they are about, checked against the live Plan.
            "quiz" -> quizInputProblems(arguments) + listOfNotNull(nodeIdProblem(arguments, history))
            "explain" -> explainInputProblems(arguments) + listOfNotNull(nodeIdProblem(arguments, history))
            else -> askOptionProblems(arguments["options"])
        }
        if (authoring.isNotEmpty()) return invalidInput(tool, authoring)
        check(tool in QuestionTools)
        return ToolOutcome(contracts.texts.questionPosed, isError = false, terminates = true)
    }

    /** Upstream's `nodeIdProblem`: the node must be in the latest Plan, whose ids are listed when it is not. */
    private fun nodeIdProblem(arguments: JsonObject, history: List<LhMessage>): String? {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull ?: return null
        val known = latestPlanTree(history)?.nodes?.map { it.id }.orEmpty()
        if (nodeId in known) return null
        return "nodeId \"$nodeId\" matches no node in this session's plan map. " + if (known.isNotEmpty()) {
            "Copy one of your plan_init slugs exactly (${known.take(5).joinToString(", ")}${if (known.size > 5) ", …" else ""})."
        } else {
            "No plan map exists yet — call plan_init before posing quizzes."
        }
    }

    /**
     * Upstream's `plan_init`: parse the mermaid block, validate the DAG, and report the node ids
     * (now binding for every quiz). The tree rides in `details` for the learner's Plan and is not
     * echoed to the model, which wrote it one message ago. A rejection lists every problem.
     */
    private fun planInit(arguments: JsonObject): ToolOutcome {
        val mermaid = arguments["mermaid"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val parsed = parseMermaidPlan(mermaid)
        if (parsed is PlanParse.Problems) return refused(parsed.problems.joinToString("\n"))
        parsed as PlanParse.Ok
        val tree = try {
            createTree(parsed.nodes, parsed.edges)
        } catch (e: PlanTreeError) {
            return refused(e.message ?: "The plan is invalid.")
        }
        return ToolOutcome(
            text = buildJsonObject {
                put("ok", true)
                put("nodeIds", JsonArray(tree.nodes.map { JsonPrimitive(it.id) }))
                put("reminder", contracts.texts.planNodeIdsReminder)
            }.toString(),
            isError = false,
            details = buildJsonObject {
                put("ok", true)
                put("tree", LhJson.encodeToJsonElement(PlanTree.serializer(), tree))
            },
        )
    }

    /** Upstream's `probe_done`: refused while a question is open or once the probe has concluded. */
    private fun probeDone(history: List<LhMessage>): ToolOutcome = when {
        hasUnresolvedQuestion(history) -> refused(
            "A question is still open — resolve it (or let him answer) before concluding the probe. " +
                "probe_done only fires with no question outstanding.",
        )
        hasProbeDone(history) -> refused("The probe is already concluded — probe_done fires exactly once.")
        else -> ToolOutcome(contracts.texts.probeDoneAck, isError = false, details = buildJsonObject { put("ok", true) })
    }

    private fun refused(reason: String): ToolOutcome {
        val body = buildJsonObject {
            put("ok", false)
            put("error", reason)
        }
        return ToolOutcome(text = body.toString(), isError = false, details = body)
    }

    /** Upstream's `invalidInputResult`: the same retryable problem list, kept as data too. */
    private fun invalidInput(tool: String, problems: List<String>): ToolOutcome = ToolOutcome(
        text = "Invalid tool input for tool \"$tool\":\n" + problems.joinToString("\n") { "- $it" },
        isError = true,
        details = buildJsonObject {
            put("tool", tool)
            put("problems", JsonArray(problems.map(::JsonPrimitive)))
        },
    )
}
