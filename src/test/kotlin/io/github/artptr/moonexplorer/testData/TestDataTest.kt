package io.github.artptr.moonexplorer.testData

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Makes sure that the test data contains the items that the tests of the plugin use. */
class TestDataTest {

    private val tasks: JsonObject =
        JsonParser.parseString(TestData.recorded("query-tasks.json")).asJsonObject.getAsJsonObject("tasks")

    private fun taskIds(project: String) = tasks.getAsJsonObject(project).keySet()

    @Test
    fun recordedWithMoon223() {
        assertEquals("moon 2.2.3", TestData.recorded("moon-version.txt").trim())
    }

    @Test
    fun workspaceHasMoonConfiguration() {
        assertTrue(TestData.workspace.resolve(".moon/workspace.yml").isFile)
    }

    @Test
    fun outputContainsAllProjectsWithTasks() {
        assertEquals(setOf("admin", "api", "db", "ui", "utils", "web", "worker"), tasks.keySet())
    }

    @Test
    fun projectWithoutTasksIsNotInOutput() {
        assertTrue(TestData.workspace.resolve("packages/config/moon.yml").isFile)
        assertFalse(tasks.has("config"))
    }

    @Test
    fun allProjectsInheritTheSameTask() {
        tasks.keySet().forEach { assertTrue("$it must have lint", "lint" in taskIds(it)) }
    }

    @Test
    fun stacksInheritDifferentTasks() {
        assertTrue("typecheck" in taskIds("web"))
        assertFalse("audit" in taskIds("web"))
        assertTrue("audit" in taskIds("api"))
        assertFalse("typecheck" in taskIds("api"))
    }

    @Test
    fun projectChangesAnInheritedTask() {
        val args = tasks.getAsJsonObject("web").getAsJsonObject("lint").getAsJsonArray("args").map { it.asString }
        assertEquals(listOf("lint", "--max-warnings", "0"), args)
    }

    @Test
    fun someTasksHaveNoDescription() {
        val all = tasks.keySet().flatMap { p -> taskIds(p).map { tasks.getAsJsonObject(p).getAsJsonObject(it) } }
        assertTrue(all.any { it.has("description") })
        assertTrue(all.any { !it.has("description") })
    }

    @Test
    fun recordedTaskFilesHaveTheirTargets() {
        for (target in listOf("api:audit", "web:lint", "web:build")) {
            val task = JsonParser.parseString(TestData.recorded("task-${target.replace(':', '-')}.json")).asJsonObject
            assertEquals(target, task.get("target").asString)
        }
    }
}
