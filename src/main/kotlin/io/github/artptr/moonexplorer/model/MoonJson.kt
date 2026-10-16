package io.github.artptr.moonexplorer.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** The output of moon has a format that the plugin cannot read. */
class MoonOutputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads the JSON output of a moon command. Throws [MoonOutputException] if [json] is not valid JSON. */
internal fun parseMoonJson(json: String): JsonElement = try {
    JsonParser.parseString(json)
} catch (e: JsonParseException) {
    throw MoonOutputException(e.message ?: "The output is not valid JSON", e)
}

internal fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

/** Gives the value of the field [name] if the value is a JSON string. If not, gives null. */
internal fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}
