package io.github.artptr.moonexplorer.testData

import com.google.gson.JsonParser
import io.github.artptr.moonexplorer.model.QueryProjectsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeQueryProjectsJsonTest {

    @Test
    fun defaultOutputHasAbout19000Lines() {
        // The product vision gives about 16,000 lines for the tasks of the example workspace. The projects add more lines.
        val lines = LargeQueryProjectsJson.generate().lines().size
        assertTrue("The output has $lines lines", lines in 17_000..21_000)
    }

    @Test
    fun parserReadsAllProjectsAndTasksOfTheDefaultOutput() {
        val projects = QueryProjectsParser.parse(LargeQueryProjectsJson.generate()).projects
        assertEquals(17, projects.size)
        assertTrue(projects.all { it.tasks.size == 14 })
    }

    @Test
    fun outputHasTheRequestedProjectsAndTasks() {
        val projects = JsonParser.parseString(LargeQueryProjectsJson.generate(3, 5)).asJsonObject.getAsJsonArray("projects")
        assertEquals(3, projects.size())
        projects.map { it.asJsonObject }.forEach { project ->
            val projectId = project.get("id").asString
            val tasks = project.getAsJsonObject("tasks")
            assertEquals(5, tasks.size())
            tasks.entrySet().forEach { (taskId, task) ->
                assertEquals(taskId, task.asJsonObject.get("id").asString)
                assertEquals("$projectId:$taskId", task.asJsonObject.get("target").asString)
            }
            assertEquals(tasks.keySet().map { "$projectId:$it" }, project.getAsJsonArray("taskTargets").map { it.asString })
        }
    }

    @Test
    fun outputHasTheSameTopLevelKeysAsMoon() {
        val output = JsonParser.parseString(LargeQueryProjectsJson.generate(1, 1)).asJsonObject
        assertEquals(setOf("projects", "options"), output.keySet())
    }
}
