package io.github.lucaskfp.learnharness.engine.question

import io.github.lucaskfp.learnharness.engine.model.LhJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The question contract and its pure grading logic, ported from upstream's
 * `src/core/question-core.ts`. Grading, shuffling and the model-facing answer record all happen
 * on the device.
 */

/** The three questions an agent can pose. Upstream: `toolQuestionKind` in `core/pending.ts`. */
@Serializable
enum class QuestionKind {
    @SerialName("quiz") Quiz,
    @SerialName("ask") Ask,
    @SerialName("explain") Explain,
}

/** The question tools (the prober's and their teacher twins), by tool name. */
val QuestionTools = setOf("quiz", "probe", "ask_user_question", "explain", "recall")

fun toolQuestionKind(tool: String): QuestionKind = when (tool) {
    "quiz", "probe" -> QuestionKind.Quiz
    "explain", "recall" -> QuestionKind.Explain
    else -> QuestionKind.Ask
}

@Serializable
data class QuizOption(val label: String, val value: String, val description: String? = null)

/** A question as armed for the learner: options in display (shuffled) order. */
data class ArmedQuestion(
    val toolCallId: String,
    val tool: String,
    val kind: QuestionKind,
    val question: String,
    val details: String?,
    val options: List<QuizOption>,
    val multiSelect: Boolean,
    /** 1-based display indices of the correct options (quiz only). */
    val correctIndices: List<Int>,
    /** The model's committed answer (explain only). */
    val committedAnswer: String?,
    val explanation: String?,
    /** The Teacher quiz's role in the cycle, `predict` or `check` (upstream `questionType`). Null elsewhere. */
    val questionType: String? = null,
) {
    /** Free text instead of options: explain, and ask without options. */
    val freeText: Boolean get() = kind == QuestionKind.Explain || (kind == QuestionKind.Ask && options.isEmpty())
}

@Serializable
data class OptionAnswer(val label: String, val value: String, val index: Int)

@Serializable
data class OptionSnapshot(val index: Int, val label: String)

/** The learner's graded answer: the full record kept in the tool result's `details`. */
@Serializable
data class QuestionAnswerData(
    val kind: QuestionKind,
    val tool: String? = null,
    /** The Teacher quiz's role, `predict` or `check`; absent on Prober questions. */
    val questionType: String? = null,
    val questionText: String,
    val options: List<OptionSnapshot>? = null,
    val dontKnow: Boolean = false,
    val answers: List<OptionAnswer> = emptyList(),
    val other: String? = null,
    val text: String? = null,
    val thinking: String? = null,
    val confidence: Int? = null,
    val correct: Boolean? = null,
    val correctIndices: List<Int>? = null,
    val committed: String? = null,
    val explanation: String? = null,
    val dismissed: Boolean? = null,
    val answeredAt: String,
)

/** What the learner submits for a question. */
data class AnswerRequest(
    val dontKnow: Boolean = false,
    /** Selected option values. */
    val values: List<String> = emptyList(),
    val other: String? = null,
    val text: String? = null,
    val thinking: String? = null,
    /** Quiz only: the learner's stated probability (0-100) that the selected answer is correct. */
    val confidence: Int? = null,
)

// ─── tolerant argument readers ──────────────────────────────────────────────────────────

private data class RawOption(val label: String, val value: String?, val description: String?)

/** Models sometimes send `options` as a JSON-encoded string; the same coercion upstream runs. */
fun parseOptionsIfString(options: JsonElement?): JsonElement? {
    if (options !is JsonPrimitive || !options.isString) return options
    return try {
        LhJson.parseToJsonElement(options.content).takeIf { it is JsonArray } ?: options
    } catch (_: Exception) {
        options
    }
}

private fun rawOptions(options: JsonElement?): List<RawOption> =
    (parseOptionsIfString(options) as? JsonArray).orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        RawOption(label, obj["value"]?.jsonPrimitive?.contentOrNull, obj["description"]?.jsonPrimitive?.contentOrNull)
    }

/** Trimmed options keyed by `value` (falling back to the label); blank labels dropped. */
fun normalizeOptions(options: JsonElement?): List<QuizOption> {
    val seen = mutableSetOf<String>()
    return rawOptions(options).mapNotNull { option ->
        val label = option.label.trim()
        if (label.isEmpty()) return@mapNotNull null
        val value = option.value?.trim()?.takeIf { it.isNotEmpty() } ?: label
        check(seen.add(value)) { "duplicate option value \"$value\"" }
        QuizOption(label, value, option.description?.trim()?.takeIf { it.isNotEmpty() })
    }
}

// ─── cross-field authoring validation ───────────────────────────────────────────────────

private val DebrisEdges = Regex("^[\\s:.\"'`]+|[\\s:.\"'`]+$")

/** Strips the copy-syntax debris a model glues onto a membership value (`":value"`, `` `value` ``). */
fun stripModelValueDebris(value: String): String = value.replace(DebrisEdges, "")

private fun optionProblems(options: List<RawOption>): Pair<MutableList<String>, List<String>> {
    val problems = mutableListOf<String>()
    val keys = mutableListOf<String>()
    options.forEachIndexed { i, option ->
        val label = option.label.trim()
        if (label.isEmpty()) {
            problems += "options[$i].label is empty — every option needs a label."
            return@forEachIndexed
        }
        val key = option.value?.trim()?.takeIf { it.isNotEmpty() } ?: label
        if (key in keys) problems += "Two options resolve to the same value \"$key\" — options must be distinct."
        keys += key
    }
    return problems to keys
}

/** Ask options: labels non-empty, values distinct. */
fun askOptionProblems(options: JsonElement?): List<String> = optionProblems(rawOptions(options)).first

/** Normalizes `correctAnswer` (a value, an array, or a JSON-array string) against the option keys. */
fun coerceCorrectAnswer(correctAnswer: JsonElement?, optionValues: List<String>): List<String> {
    fun clean(value: String): String {
        val trimmed = value.trim()
        return if (trimmed in optionValues) trimmed else stripModelValueDebris(value)
    }
    return when (correctAnswer) {
        is JsonArray -> correctAnswer.map { clean(it.jsonPrimitive.contentOrNull.orEmpty()) }
        is JsonPrimitive -> {
            val trimmed = correctAnswer.content.trim()
            when {
                trimmed in optionValues -> listOf(trimmed)
                trimmed.startsWith("[") && trimmed.endsWith("]") -> try {
                    (LhJson.parseToJsonElement(trimmed) as JsonArray).map { clean(it.jsonPrimitive.content) }
                } catch (_: Exception) {
                    listOf(clean(correctAnswer.content))
                }
                else -> listOf(clean(correctAnswer.content))
            }
        }
        else -> emptyList()
    }
}

/** The quiz authoring contract: option sanity, and a `correctAnswer` the grader can resolve. */
fun quizInputProblems(input: JsonObject): List<String> {
    val (problems, keys) = optionProblems(rawOptions(input["options"]))
    val unique = coerceCorrectAnswer(input["correctAnswer"], keys).distinct()
    if (unique.isEmpty() || unique.any { it.isEmpty() }) {
        problems += "correctAnswer is required — provide the value (or label) of the correct option(s)."
        return problems
    }
    for (v in unique) {
        if (v !in keys) {
            problems += "correctAnswer \"$v\" does not match any option value. Valid values: " +
                keys.joinToString(", ") { "\"$it\"" } + ". " +
                "Copy the exact value (or label) of the intended option — never a paraphrase or slug."
        }
    }
    val multi = input["multiSelect"]?.jsonPrimitive?.booleanOrNull == true
    if (unique.size > 1 && !multi) {
        problems += "correctAnswer lists ${unique.size} values but multiSelect is not true — a single-select " +
            "question with several correct answers can never be answered correctly. Set multiSelect: true, " +
            "or make exactly one option correct."
    }
    return problems
}

/** The explain (free-recall) contract: the commitment must exist. */
fun explainInputProblems(input: JsonObject): List<String> =
    if (input["correctAnswer"]?.jsonPrimitive?.contentOrNull.orEmpty().isBlank()) {
        listOf("correctAnswer is required — commit to the full correct answer BEFORE posing the question.")
    } else {
        emptyList()
    }

// ─── shuffling ──────────────────────────────────────────────────────────────────────────

/** mulberry32 seeded by a string (upstream `seededRandom`): the same seed always shuffles alike. */
fun seededRandom(seed: String): () -> Double {
    var h = 2166136261L.toInt()
    for (ch in seed) {
        h = h xor ch.code
        h *= 16777619
    }
    var a = h
    return {
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/** Fisher–Yates over a copy. */
fun shuffleOptions(options: List<QuizOption>, random: () -> Double): List<QuizOption> {
    val out = options.toMutableList()
    for (i in out.size - 1 downTo 1) {
        val j = kotlin.math.floor(random() * (i + 1)).toInt()
        val tmp = out[i]
        out[i] = out[j]
        out[j] = tmp
    }
    return out
}

/** Value(s) to 1-based display indices. Runs after shuffling. */
fun resolveCorrect(correctAnswer: JsonElement?, options: List<QuizOption>): List<Int> {
    val values = coerceCorrectAnswer(correctAnswer, options.map { it.value })
    val byValue = options.withIndex().associate { (i, o) -> o.value to i + 1 }
    return values.mapNotNull { byValue[it.trim()] }.distinct().sorted()
}

fun isCorrect(selected: List<Int>, correct: List<Int>): Boolean =
    selected.size == correct.size && selected.sorted() == correct.sorted()

// ─── arming and answering ───────────────────────────────────────────────────────────────

/**
 * Arms a posed question call for the learner. Quiz options are shuffled with a seed derived from
 * the call id, so the order is stable across reloads but differs per question.
 * Returns null for a call that is not a gradeable question.
 */
/** Upstream's `normalizeQuizType`: `predict` or `check`, anything else is no type. */
fun normalizeQuizType(raw: String?): String? = when (raw?.trim()?.trim('"', '`')?.lowercase()) {
    "predict" -> "predict"
    "check" -> "check"
    else -> null
}

fun armQuestion(toolCallId: String, tool: String, input: JsonObject): ArmedQuestion? {
    val question = input["question"]?.jsonPrimitive?.contentOrNull ?: return null
    val details = input["details"]?.jsonPrimitive?.contentOrNull
    val kind = toolQuestionKind(tool)
    return try {
        when (kind) {
            QuestionKind.Quiz -> {
                val options = shuffleOptions(normalizeOptions(input["options"]), seededRandom(toolCallId))
                ArmedQuestion(
                    toolCallId, tool, kind, question, details, options,
                    multiSelect = input["multiSelect"]?.jsonPrimitive?.booleanOrNull == true,
                    correctIndices = resolveCorrect(input["correctAnswer"], options),
                    committedAnswer = null,
                    explanation = input["explanation"]?.jsonPrimitive?.contentOrNull,
                    questionType = normalizeQuizType(input["type"]?.jsonPrimitive?.contentOrNull),
                )
            }
            QuestionKind.Explain -> ArmedQuestion(
                toolCallId, tool, kind, question, details, emptyList(), multiSelect = false,
                correctIndices = emptyList(),
                committedAnswer = input["correctAnswer"]?.jsonPrimitive?.contentOrNull,
                explanation = null,
            )
            QuestionKind.Ask -> ArmedQuestion(
                toolCallId, tool, kind, question, details, normalizeOptions(input["options"]),
                multiSelect = input["multiSelect"]?.jsonPrimitive?.booleanOrNull == true,
                correctIndices = emptyList(), committedAnswer = null, explanation = null,
            )
        }
    } catch (_: IllegalStateException) {
        null
    }
}

/** Turns an [AnswerRequest] into the full answer record. Quiz answers are graded here. */
fun buildAnswer(armed: ArmedQuestion, request: AnswerRequest, answeredAt: String): QuestionAnswerData {
    val byValue = armed.options.withIndex().associate { (i, o) -> o.value to OptionAnswer(o.label, o.value, i + 1) }
    val answers = request.values.mapNotNull { byValue[it] }.sortedBy { it.index }
    val dontKnow = request.dontKnow
    var data = QuestionAnswerData(
        kind = armed.kind,
        tool = armed.tool,
        questionType = armed.questionType,
        questionText = armed.question,
        options = armed.options.mapIndexed { i, o -> OptionSnapshot(i + 1, o.label) },
        dontKnow = dontKnow,
        answers = answers,
        other = request.other?.trim()?.takeIf { it.isNotEmpty() },
        text = request.text?.trim()?.takeIf { it.isNotEmpty() },
        thinking = request.thinking?.trim()?.takeIf { it.isNotEmpty() },
        answeredAt = answeredAt,
    )
    if (armed.kind == QuestionKind.Quiz) {
        data = data.copy(
            correctIndices = armed.correctIndices,
            explanation = armed.explanation,
            correct = if (dontKnow) false else isCorrect(answers.map { it.index }, armed.correctIndices),
            // The confidence slider rides on real answers only, never on "I don't know".
            confidence = if (!dontKnow) request.confidence?.coerceIn(0, 100) else null,
        )
    }
    if (armed.kind == QuestionKind.Explain) data = data.copy(committed = armed.committedAnswer)
    return data
}

/**
 * What the model reads: only what the learner added. The call's own arguments already carry the
 * question, options and explanation, so restating them would double every answer's tokens.
 */
fun compactAnswerForModel(answer: QuestionAnswerData): JsonObject = buildJsonObject {
    put("kind", LhJson.encodeToJsonElement(QuestionKind.serializer(), answer.kind))
    if (answer.dismissed == true) {
        put("dismissed", true)
        return@buildJsonObject
    }
    if (answer.dontKnow) put("dontKnow", true)
    if (answer.answers.isNotEmpty()) {
        put("answers", buildJsonArray {
            answer.answers.forEach { add(buildJsonObject { put("index", it.index); put("label", it.label) }) }
        })
    }
    answer.text?.let { put("text", it) }
    answer.other?.let { put("other", it) }
    // The verdict and stated confidence are the learner's performance: new information.
    if (answer.correct != null && !answer.dontKnow) put("correct", answer.correct)
    answer.confidence?.let { put("confidence", it) }
    answer.thinking?.let { put("thinking", it) }
}
