package io.github.artptr.moonexplorer.testData

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Makes `moon query projects` output for a large workspace.
 *
 * The function copies the projects and tasks of the recorded output and gives them new project IDs and task IDs.
 * Thus the output has the same structure as the output of moon.
 * The function changes only `id`, `target`, `tasks`, and `taskTargets`. Other values, for example `root`, `deps`, and `inputGlobs`,
 * stay the same as in the recorded project or task. Use this output for tests of size and speed, not for tests of these values.
 * The default values make output for a workspace with the same size as the example workspace in the product vision:
 * 17 projects and 14 tasks in each project.
 */
object LargeQueryProjectsJson {

    fun generate(projectCount: Int = 17, tasksPerProject: Int = 14): String {
        val recorded = JsonParser.parseString(TestData.recorded("query-projects.json")).asJsonObject
        val projectTemplates = recorded.getAsJsonArray("projects").map { it.asJsonObject }.filter { it.has("tasks") }
        val taskTemplates = projectTemplates.flatMap { project ->
            project.getAsJsonObject("tasks").entrySet().map { it.value.asJsonObject }
        }

        val projects = JsonArray()
        var next = 0
        for (p in 1..projectCount) {
            val projectId = "project-%02d".format(p)
            val project = projectTemplates[(p - 1) % projectTemplates.size].deepCopy()
            val tasks = JsonObject()
            val targets = JsonArray()
            for (t in 1..tasksPerProject) {
                val taskId = "task-%02d".format(t)
                val task = taskTemplates[next++ % taskTemplates.size].deepCopy()
                task.addProperty("id", taskId)
                task.addProperty("target", "$projectId:$taskId")
                tasks.add(taskId, task)
                targets.add("$projectId:$taskId")
            }
            project.addProperty("id", projectId)
            project.add("tasks", tasks)
            project.add("taskTargets", targets)
            projects.add(project)
        }

        val output = JsonObject()
        output.add("projects", projects)
        output.add("options", recorded.get("options").deepCopy())
        return GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(output)
    }
}
