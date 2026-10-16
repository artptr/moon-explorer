package io.github.artptr.moonexplorer.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** The output of moon has a format that the plugin cannot read. */
class MoonOutputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads the JSON output of `moon query tasks`. */
object QueryTasksParser {

    /** Reads [json] and gives the projects and tasks, sorted by ID. Throws [MoonOutputException] if the JSON is not correct. */
    fun parse(json: String): WorkspaceTasks {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw MoonOutputException(e.message ?: "The output is not valid JSON", e)
        }
        val tasks = root.objectOrNull()?.get("tasks")?.objectOrNull()
            ?: throw MoonOutputException("The output does not contain the object \"tasks\"")

        val projects = tasks.entrySet().mapNotNull { (projectId, projectTasks) ->
            val taskList = projectTasks.objectOrNull()?.entrySet().orEmpty().mapNotNull { (key, value) ->
                val task = value.objectOrNull() ?: return@mapNotNull null
                val id = task.string("id") ?: key
                MoonTask(
                    id = id,
                    target = task.string("target") ?: "$projectId:$id",
                    description = task.string("description")?.takeIf { it.isNotBlank() },
                )
            }
            if (taskList.isEmpty()) return@mapNotNull null
            MoonProject(projectId, taskList.sortedWith(compareBy(ID_ORDER) { it.id }))
        }
        return WorkspaceTasks(projects.sortedWith(compareBy(ID_ORDER) { it.id }))
    }

    private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }
}
