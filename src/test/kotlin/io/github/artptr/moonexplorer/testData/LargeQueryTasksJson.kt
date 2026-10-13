package io.github.artptr.moonexplorer.testData

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Makes `moon query tasks` output for a large workspace.
 *
 * The function copies the tasks of the recorded output and gives them new project IDs and task IDs.
 * Thus the output has the same structure as the output of moon.
 * The function changes only `id` and `target`. Other values, for example `deps`, `inputGlobs`, and `outputFiles`,
 * stay the same as in the recorded task. Use this output for tests of size and speed, not for tests of these values.
 * The default values make output for a workspace with the same size as the example workspace in the product vision:
 * 17 projects, 14 tasks in each project, and about 16,000 lines.
 */
object LargeQueryTasksJson {

    fun generate(projectCount: Int = 17, tasksPerProject: Int = 14): String {
        val recorded = JsonParser.parseString(TestData.recorded("query-tasks.json")).asJsonObject
        val templates = recorded.getAsJsonObject("tasks").entrySet()
            .flatMap { (_, tasks) -> tasks.asJsonObject.entrySet().map { it.value.asJsonObject } }

        val tasks = JsonObject()
        var next = 0
        for (p in 1..projectCount) {
            val projectId = "project-%02d".format(p)
            val projectTasks = JsonObject()
            for (t in 1..tasksPerProject) {
                val taskId = "task-%02d".format(t)
                val task = templates[next++ % templates.size].deepCopy()
                task.addProperty("id", taskId)
                task.addProperty("target", "$projectId:$taskId")
                projectTasks.add(taskId, task)
            }
            tasks.add(projectId, projectTasks)
        }

        val output = JsonObject()
        output.add("tasks", tasks)
        output.add("options", recorded.get("options").deepCopy())
        return GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(output)
    }
}
