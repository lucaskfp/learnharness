package io.github.lucaskfp.learnharness.engine.tools

import io.github.lucaskfp.learnharness.engine.model.LhJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validates a tool call's arguments against the tool's JSON Schema, for the subset TypeBox emits
 * for upstream's question tools: `type`, `properties`, `required`, `items`, `minItems`, `anyOf`,
 * `const` and `enum`. A failing call returns to the model as a correctable error, as upstream's
 * `validateToolArguments` does.
 */
object SchemaValidator {
    /** Problems as `path: message` lines; empty when [value] satisfies [schema]. */
    fun validate(schema: JsonObject, value: JsonElement): List<String> {
        val problems = mutableListOf<String>()
        check(schema, value, "", problems)
        return problems
    }

    /**
     * Pre-validation normalization upstream runs: `null` for an optional property is dropped, and
     * a string standing in for a boolean or number is converted.
     */
    fun normalize(schema: JsonObject, value: JsonElement): JsonElement {
        val anyOf = schema["anyOf"] as? JsonArray
        if (anyOf != null && value !is JsonObject && value !is JsonArray) return value
        return when (value) {
            is JsonObject -> {
                val properties = (schema["properties"] as? JsonObject).orEmpty()
                val required = (schema["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()
                JsonObject(
                    value.mapNotNull { (key, child) ->
                        val childSchema = properties[key] as? JsonObject
                        when {
                            child is JsonNull && key !in required -> null
                            childSchema == null -> key to child
                            else -> key to normalize(childSchema, child)
                        }
                    }.toMap(),
                )
            }
            is JsonArray -> {
                val items = schema["items"] as? JsonObject
                if (items == null) value else JsonArray(value.map { normalize(items, it) })
            }
            is JsonPrimitive -> convert(schema, value)
        }
    }

    private fun convert(schema: JsonObject, value: JsonPrimitive): JsonElement {
        if (!value.isString) return value
        return when (schema["type"]?.jsonPrimitive?.content) {
            "boolean" -> value.content.trim().lowercase().let {
                if (it == "true") JsonPrimitive(true) else if (it == "false") JsonPrimitive(false) else value
            }
            "number", "integer" -> value.content.trim().toDoubleOrNull()?.let { number ->
                if (schema["type"]?.jsonPrimitive?.content == "integer" && number % 1.0 != 0.0) value
                else if (number % 1.0 == 0.0) JsonPrimitive(number.toLong()) else JsonPrimitive(number)
            } ?: value
            else -> value
        }
    }

    private fun check(schema: JsonObject, value: JsonElement, path: String, problems: MutableList<String>) {
        schema["anyOf"]?.let { anyOf ->
            val branches = anyOf.jsonArray.map { it.jsonObject }
            if (branches.none { branch -> mutableListOf<String>().also { check(branch, value, path, it) }.isEmpty() }) {
                problems += "${at(path)}: must match a schema in anyOf"
            }
            return
        }
        schema["const"]?.let { const ->
            if (value != const) problems += "${at(path)}: must be equal to constant"
            return
        }
        schema["enum"]?.let { values ->
            if (values.jsonArray.none { it == value }) problems += "${at(path)}: must be equal to one of the allowed values"
            return
        }
        val type = schema["type"]?.jsonPrimitive?.content
        when (type) {
            "object" -> {
                if (value !is JsonObject) {
                    problems += "${at(path)}: must be object"
                    return
                }
                val required = (schema["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
                for (key in required) {
                    if (key !in value) problems += "${at(path)}: must have required property '$key'"
                }
                val properties = (schema["properties"] as? JsonObject).orEmpty()
                for ((key, childSchema) in properties) {
                    value[key]?.let { check(childSchema.jsonObject, it, "$path/$key", problems) }
                }
            }
            "array" -> {
                if (value !is JsonArray) {
                    problems += "${at(path)}: must be array"
                    return
                }
                schema["minItems"]?.jsonPrimitive?.intOrNull?.let { min ->
                    if (value.size < min) problems += "${at(path)}: must NOT have fewer than $min items"
                }
                (schema["items"] as? JsonObject)?.let { items ->
                    value.forEachIndexed { i, element -> check(items, element, "$path/$i", problems) }
                }
            }
            "string" -> if (value !is JsonPrimitive || !value.isString) problems += "${at(path)}: must be string"
            "boolean" -> if (value !is JsonPrimitive || value.booleanOrNull == null || value.isString) {
                problems += "${at(path)}: must be boolean"
            }
            "number", "integer" -> if (value !is JsonPrimitive || value.isString || value.doubleOrNull == null) {
                problems += "${at(path)}: must be $type"
            }
            "null" -> if (value !is JsonNull) problems += "${at(path)}: must be null"
            else -> Unit
        }
    }

    private fun at(path: String) = path.ifEmpty { "/" }

    /** The text a failed validation returns to the model; upstream's wording. */
    fun errorText(tool: String, problems: List<String>, arguments: JsonElement): String =
        "Validation failed for tool \"$tool\":\n" + problems.joinToString("\n") { "  - $it" } +
            "\n\nReceived arguments:\n" + LhJson.encodeToString(JsonElement.serializer(), arguments)
}
