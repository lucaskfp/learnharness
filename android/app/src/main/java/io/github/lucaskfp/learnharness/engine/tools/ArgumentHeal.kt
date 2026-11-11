package io.github.lucaskfp.learnharness.engine.tools

import io.github.lucaskfp.learnharness.engine.model.LhJson
import io.github.lucaskfp.learnharness.engine.question.parseOptionsIfString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The weak-model argument heal (upstream `src/core/argument-heal.ts`): a string where the schema
 * expects a literal, boolean, null, number or array is JSON-parsed and kept when the parsed value
 * fits. Well-formed arguments pass through untouched; whatever cannot be healed is left for
 * validation to reject honestly. Runs before validation.
 */
object ArgumentHeal {
    fun heal(arguments: JsonObject, schema: JsonObject): JsonObject {
        val properties = (schema["properties"] as? JsonObject) ?: return arguments
        var healed: MutableMap<String, JsonElement>? = null
        for ((key, propElement) in properties) {
            val raw = arguments[key] as? JsonPrimitive ?: continue
            if (!raw.isString) continue
            val prop = propElement.jsonObject
            val parsed = tryParse(raw.content)
            val literals = literalsOf(prop)
            var fixed: JsonElement? = null
            if (literals.isNotEmpty()) {
                if (raw in literals) continue // already a valid member
                if (parsed != null && parsed in literals) fixed = parsed
            }
            if (fixed == null && acceptsNull(prop) && parsed is JsonNull) fixed = JsonNull
            val type = prop["type"]?.jsonPrimitive?.content
            if (fixed == null && type == "boolean" && parsed is JsonPrimitive && !parsed.isString && parsed.booleanOrNull != null) fixed = parsed
            if (fixed == null && (type == "number" || type == "integer") && parsed is JsonPrimitive && !parsed.isString && parsed.doubleOrNull != null) fixed = parsed
            if (fixed == null && type == "array" && parsed is JsonArray) fixed = parsed
            if (fixed != null) {
                if (healed == null) healed = arguments.toMutableMap()
                healed[key] = fixed
            }
        }
        return healed?.let(::JsonObject) ?: arguments
    }

    /** The registry-wide standard heal, composed after a tool's own argument preparation. */
    fun prepare(tool: String, arguments: JsonObject, schema: JsonObject): JsonObject {
        val unwrapped = if (tool in OptionsTools) {
            arguments["options"]?.let { options ->
                val parsed = parseOptionsIfString(options)
                if (parsed !== options) JsonObject(arguments + ("options" to parsed!!)) else arguments
            } ?: arguments
        } else {
            arguments
        }
        return heal(unwrapped, schema)
    }

    private val OptionsTools = setOf("probe", "quiz", "ask_user_question")

    private fun literalsOf(prop: JsonObject): List<JsonElement> {
        (prop["anyOf"] as? JsonArray)?.let { branches -> return branches.flatMap { literalsOf(it.jsonObject) } }
        return prop["const"]?.let { listOf(it) } ?: emptyList()
    }

    private fun acceptsNull(prop: JsonObject): Boolean =
        prop["type"]?.jsonPrimitive?.content == "null" ||
            (prop["anyOf"] as? JsonArray).orEmpty().any { it.jsonObject["type"]?.jsonPrimitive?.content == "null" }

    private fun tryParse(raw: String): JsonElement? = try {
        LhJson.parseToJsonElement(raw)
    } catch (_: Exception) {
        null
    }
}
