package io.github.artptr.moonexplorer.testData

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Makes sure that the test data contains the items that the tests of the plugin use. */
class TestDataTest {

    private val projects: List<JsonObject> =
        JsonParser.parseString(TestData.recorded("query-projects.json")).asJsonObject.getAsJsonArray("projects").map { it.asJsonObject }

    /** The tasks of each project that has tasks. The keys are project IDs. */
    private val tasks: Map<String, JsonObject> =
        projects.filter { it.has("tasks") }.associate { it.get("id").asString to it.getAsJsonObject("tasks") }

    private fun taskIds(project: String) = tasks.getValue(project).keySet()

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
        assertEquals(setOf("admin", "api", "db", "ui", "utils", "web", "worker"), tasks.keys)
    }

    @Test
    fun projectWithoutTasksHasNoTasksKey() {
        assertTrue(TestData.workspace.resolve("packages/config/moon.yml").isFile)
        val config = projects.single { it.get("id").asString == "config" }
        assertFalse(config.has("tasks"))
    }

    @Test
    fun allProjectsInheritTheSameTask() {
        tasks.keys.forEach { assertTrue("$it must have lint", "lint" in taskIds(it)) }
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
        val args = tasks.getValue("web").getAsJsonObject("lint").getAsJsonArray("args").map { it.asString }
        assertEquals(listOf("lint", "--max-warnings", "0"), args)
    }

    @Test
    fun someTasksHaveNoDescription() {
        val all = tasks.values.flatMap { projectTasks -> projectTasks.entrySet().map { it.value.asJsonObject } }
        assertTrue(all.any { it.has("description") })
        assertTrue(all.any { !it.has("description") })
    }

    /**
     * The commands of the test workspace do not make files. If a task does not make a necessary output, moon stops with an error.
     * Thus all outputs must be optional. Then `moon run` completes for each task in a manual test.
     */
    @Test
    fun allOutputFilesAreOptional() {
        val outputFiles = tasks.values.flatMap { projectTasks ->
            projectTasks.entrySet().mapNotNull { it.value.asJsonObject.getAsJsonObject("outputFiles") }.flatMap { it.entrySet() }
        }
        assertTrue(outputFiles.isNotEmpty())
        outputFiles.forEach { assertTrue("${it.key} must be optional", it.value.asJsonObject.get("optional")?.asBoolean == true) }
    }

    /**
     * moon writes an internal task only in `taskTargets` and in the configuration of the project.
     * The plugin reads the key `tasks`. Thus the tree does not show internal tasks. `moon run` does not run them.
     */
    @Test
    fun internalTaskIsOnlyInTheConfigurationAndTaskTargets() {
        val ui = projects.single { it.get("id").asString == "ui" }
        assertTrue("ui:generate-icons" in ui.getAsJsonArray("taskTargets").map { it.asString })
        assertTrue(ui.getAsJsonObject("config").getAsJsonObject("tasks").has("generate-icons"))
        assertFalse("generate-icons" in taskIds("ui"))
    }

    /** The groups by `layer` and by tag, and the icon for the language of each project, use these values. They do not need one more moon command. */
    @Test
    fun projectsHaveLayerTagsAndLanguage() {
        val web = projects.single { it.get("id").asString == "web" }
        assertEquals("application", web.get("layer").asString)
        assertEquals("typescript", web.get("language").asString)
        assertEquals(listOf("react", "public"), web.getAsJsonObject("config").getAsJsonArray("tags").map { it.asString })
    }

    /**
     * A tooltip with the command can use these values.
     * moon sets the type from the options of the task. A task with outputs has the type `build`, a persistent task has the type `run`, and all other tasks have the type `test`.
     * Thus `lint` and `format` are `test`. An icon for each type can be incorrect for these tasks.
     */
    @Test
    fun tasksHaveTypeAndCommand() {
        val webTasks = tasks.getValue("web")
        assertEquals("build", webTasks.getAsJsonObject("build").get("type").asString)
        assertEquals("run", webTasks.getAsJsonObject("dev").get("type").asString)
        assertEquals("test", webTasks.getAsJsonObject("test").get("type").asString)
        assertEquals("test", webTasks.getAsJsonObject("lint").get("type").asString)
        assertEquals("test", webTasks.getAsJsonObject("format").get("type").asString)
        assertEquals("echo", webTasks.getAsJsonObject("build").get("command").asString)
        assertEquals(listOf("build", "web"), webTasks.getAsJsonObject("build").getAsJsonArray("args").map { it.asString })
    }

    @Test
    fun recordedTaskFilesHaveTheirTargets() {
        for (target in listOf("api:audit", "web:lint", "web:build")) {
            val task = JsonParser.parseString(TestData.recorded("task-${target.replace(':', '-')}.json")).asJsonObject
            assertEquals(target, task.get("target").asString)
        }
    }

    @Test
    fun recordedProjectsHaveNoPathOfTheComputer() {
        val roots = projects.map { it.get("root").asString }
        assertTrue(roots.all { it.startsWith("/workspace/") })
    }
}
