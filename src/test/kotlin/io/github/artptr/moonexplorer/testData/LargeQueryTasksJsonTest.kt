package io.github.artptr.moonexplorer.testData

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeQueryTasksJsonTest {

    @Test
    fun defaultOutputHasAbout16000Lines() {
        val lines = LargeQueryTasksJson.generate().lines().size
        assertTrue("The output has $lines lines", lines in 15_000..17_000)
    }

    @Test
    fun outputHasTheRequestedProjectsAndTasks() {
        val tasks = JsonParser.parseString(LargeQueryTasksJson.generate(3, 5)).asJsonObject.getAsJsonObject("tasks")
        assertEquals(3, tasks.size())
        tasks.entrySet().forEach { (projectId, projectTasks) ->
            assertEquals(5, projectTasks.asJsonObject.size())
            projectTasks.asJsonObject.entrySet().forEach { (taskId, task) ->
                assertEquals(taskId, task.asJsonObject.get("id").asString)
                assertEquals("$projectId:$taskId", task.asJsonObject.get("target").asString)
            }
        }
    }

    @Test
    fun outputHasTheSameTopLevelKeysAsMoon() {
        val output = JsonParser.parseString(LargeQueryTasksJson.generate(1, 1)).asJsonObject
        assertEquals(setOf("tasks", "options"), output.keySet())
    }
}
