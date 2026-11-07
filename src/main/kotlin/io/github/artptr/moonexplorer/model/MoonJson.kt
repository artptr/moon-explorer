package io.github.artptr.moonexplorer.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** Tells why the plugin cannot read the output of moon. */
sealed interface OutputProblem {
    /** The output is not valid JSON. [detail] is the error text from the JSON parser, or null if the parser gave no text. [detail] can be blank. */
    data class NotJson(val detail: String?) : OutputProblem

    /** The output does not contain the array `projects`. */
    data object NoProjects : OutputProblem
}

/** The output of moon has a format that the plugin cannot read. [problem] tells the cause. */
class MoonOutputException(val problem: OutputProblem, cause: Throwable? = null) : Exception(problem.toString(), cause)

/** Reads the JSON output of a moon command. Throws [MoonOutputException] if [json] is not valid JSON. */
internal fun parseMoonJson(json: String): JsonElement = try {
    JsonParser.parseString(json)
} catch (e: JsonParseException) {
    throw MoonOutputException(OutputProblem.NotJson(e.message), e)
}

internal fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

internal fun JsonElement.arrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null

/** Gives the value of the field [name] if the value is a JSON object. If not, gives null. */
internal fun JsonObject.obj(name: String): JsonObject? = get(name)?.objectOrNull()

/** Gives the value of the field [name] if the value is a JSON string. If not, gives null. */
internal fun JsonObject.string(name: String): String? = get(name)?.stringOrNull()

/**
 * Gives the merge strategies of [task] that are not the default value `append`. The keys are the names of the fields, for example `args` for
 * `options.mergeArgs`. The result keeps the sequence of the output. The function also finds the `merge*` fields that a later moon version adds.
 */
internal fun mergeStrategies(task: JsonObject): Map<String, String> = buildMap {
    task.obj("options")?.entrySet().orEmpty().forEach { (key, value) ->
        val strategy = value.stringOrNull()
        if (key.length > MERGE_PREFIX.length && key.startsWith(MERGE_PREFIX) && strategy != null && strategy != DEFAULT_MERGE_STRATEGY) {
            put(key.removePrefix(MERGE_PREFIX).replaceFirstChar(Char::lowercase), strategy)
        }
    }
}

/**
 * Gives the merge strategies in the configuration of [task], from `config.tasks`. The output of moon has the setting `merge` without change in this location.
 * This setting applies to all fields. A setting for one field, for example `mergeArgs`, has priority over it.
 */
internal fun configuredMergeStrategies(task: JsonObject): Map<String, String> {
    val options = task.obj("options") ?: return emptyMap()
    val shorthand = options.string(MERGE_PREFIX)?.takeUnless { it == DEFAULT_MERGE_STRATEGY } ?: return mergeStrategies(task)
    val fields = MERGE_FIELDS.filter { options.string(MERGE_PREFIX + it.replaceFirstChar(Char::uppercase)) == null }
    return (fields.associateWith { shorthand } + mergeStrategies(task)).toSortedMap()
}

/** The fields that the setting `merge` of moon 2.6.4 applies to. */
private val MERGE_FIELDS = listOf("args", "checks", "deps", "env", "inputs", "outputs", "tags", "toolchains")

private const val MERGE_PREFIX = "merge"
private const val DEFAULT_MERGE_STRATEGY = "append"

/** Gives the JSON strings in the array. Gives an empty list if the value is not an array. The function ignores values that are not strings. */
internal fun JsonElement.strings(): List<String> = arrayOrNull()?.mapNotNull { it.stringOrNull() }.orEmpty()

/** Gives the value if it is a JSON string. If not, gives null. */
internal fun JsonElement.stringOrNull(): String? = if (isJsonPrimitive && asJsonPrimitive.isString) asString else null
