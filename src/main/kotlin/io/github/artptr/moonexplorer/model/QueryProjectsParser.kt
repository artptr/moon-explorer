package io.github.artptr.moonexplorer.model

import com.google.gson.JsonObject

/**
 * Reads the JSON output of `moon query projects`.
 * In moon v2, each project in the output contains its tasks. The output does not contain internal tasks.
 */
object QueryProjectsParser {

    /** moon gives this `stack` value if a project does not set `stack`. */
    private const val UNKNOWN_STACK = "unknown"

    /**
     * Reads [json] and gives the projects that have tasks, sorted by ID. The function also sorts the tasks of each project by ID.
     * Throws [MoonOutputException] if the JSON is not correct.
     */
    fun parse(json: String): WorkspaceTasks {
        val projects = parseMoonJson(json).objectOrNull()?.get("projects")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw MoonOutputException("The output does not contain the array \"projects\"")

        val result = projects.mapNotNull { it.objectOrNull() }.mapNotNull { project ->
            val projectId = project.string("id") ?: return@mapNotNull null
            // moon does not write the key "tasks" if the project has no tasks.
            val tasks = project.get("tasks")?.objectOrNull()?.entrySet().orEmpty().mapNotNull { (key, value) ->
                val task = value.objectOrNull() ?: return@mapNotNull null
                val id = task.string("id") ?: key
                MoonTask(
                    id = id,
                    target = task.string("target") ?: "$projectId:$id",
                    description = task.string("description")?.takeIf { it.isNotBlank() },
                )
            }
            if (tasks.isEmpty()) return@mapNotNull null
            MoonProject(projectId, tasks.sortedWith(compareBy(ID_ORDER) { it.id }), stack(project))
        }
        return WorkspaceTasks(result.sortedWith(compareBy(ID_ORDER) { it.id }))
    }

    /** Gives the `stack` value of [project], or null if the project does not set `stack`. */
    private fun stack(project: JsonObject): String? {
        val stack = project.string("stack") ?: project.get("config")?.objectOrNull()?.string("stack")
        return stack?.takeUnless { it.isBlank() || it.equals(UNKNOWN_STACK, ignoreCase = true) }
    }
}
