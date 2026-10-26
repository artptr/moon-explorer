package io.github.artptr.moonexplorer.model

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

/** Gives the value of the field [name] if the value is a JSON string. If not, gives null. */
internal fun JsonObject.string(name: String): String? = get(name)?.stringOrNull()

/** Gives the value if it is a JSON string. If not, gives null. */
internal fun JsonElement.stringOrNull(): String? = if (isJsonPrimitive && asJsonPrimitive.isString) asString else null
