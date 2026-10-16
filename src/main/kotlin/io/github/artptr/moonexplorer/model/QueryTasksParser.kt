package io.github.artptr.moonexplorer.model

/** Reads the JSON output of `moon query tasks`. */
object QueryTasksParser {

    /** Reads [json] and gives the projects and tasks, sorted by ID. Throws [MoonOutputException] if the JSON is not correct. */
    fun parse(json: String): WorkspaceTasks {
        val tasks = parseMoonJson(json).objectOrNull()?.get("tasks")?.objectOrNull()
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
}
