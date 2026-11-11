package io.github.lucaskfp.learnharness.engine.tools

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
import kotlinx.serialization.json.put

/** How the Prober quizzes: graded multiple choice (`probe`) or free recall (`recall`). */
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
 * The Prober's tools for this slice: `probe` or `recall` (one quizzing tool at a time, by
 * mechanics) and `ask_user_question`, with upstream's argument healing before validation and its
 * authoring refinements after. A question that passes ends the Turn; one that fails returns to
 * the model as a correctable error.
 */
class ProberTools(private val contracts: ToolContracts, mechanics: QuizMechanics = QuizMechanics.Choice) {
    private val quizTool = if (mechanics == QuizMechanics.Choice) "probe" else "recall"

    val specs: List<ToolSpec> = listOf(contracts.spec(quizTool), contracts.spec("ask_user_question"))

    fun execute(call: ToolCall): ToolOutcome {
        val spec = specs.firstOrNull { it.name == call.name }
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
        val authoring = when (call.name) {
            "probe" -> quizInputProblems(arguments)
            "recall" -> explainInputProblems(arguments)
            else -> askOptionProblems(arguments["options"])
        }
        if (authoring.isNotEmpty()) return invalidInput(call.name, authoring)
        check(call.name in QuestionTools)
        return ToolOutcome(contracts.texts.questionPosed, isError = false, terminates = true)
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
