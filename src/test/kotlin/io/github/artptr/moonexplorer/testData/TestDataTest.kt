package io.github.artptr.moonexplorer.testData

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Makes sure that the test data contains the items that the tests of the plugin use. */
class TestDataTest {

    private val projects: List<JsonObject> =
        JsonParser.parseString(TestData.recorded("query-projects.json")).asJsonObject.getAsJsonArray("projects").map { it.asJsonObject }

    /** The tasks of each project that has tasks. The keys are project IDs. */
    private val tasks: Map<String, JsonObject> =
        projects.filter { it.has("tasks") }.associate { it.get("id").asString to it.getAsJsonObject("tasks") }

    private fun taskIds(project: String) = tasks.getValue(project).keySet()

    /** The `Moon output` workflow records the output with a newer moon version. Thus the test compares the recorded version with the version in `workspace/package.json`. */
    @Test
    fun recordedWithMoonVersionOfWorkspace() {
        val packageJson = JsonParser.parseString(TestData.workspace.resolve("package.json").readText()).asJsonObject
        val version = packageJson.getAsJsonObject("devDependencies").get("@moonrepo/cli").asString
        assertEquals("moon $version", TestData.recorded("moon-version.txt").trim())
    }

    @Test
    fun recordedTaskGraphContainsInternalTasks() {
        val graph = JsonParser.parseString(TestData.recorded("task-graph.json")).asJsonObject
        val targets = graph.getAsJsonObject("data").entrySet().map { it.value.asJsonObject.get("target").asString }
        assertTrue("ui:generate-icons" in targets)
        // The parser reads only the dependencies of the tasks. They must give the same number of items as the edges of the graph.
        val dependencies = graph.getAsJsonObject("data").entrySet().sumOf { it.value.asJsonObject.getAsJsonArray("deps")?.size() ?: 0 }
        assertEquals(graph.getAsJsonObject("graph").getAsJsonArray("edges").size(), dependencies)
    }

    @Test
    fun allProjectsInheritTheSameTask() {
        tasks.keys.forEach { assertTrue("$it must have lint", "lint" in taskIds(it)) }
    }

    @Test
    fun projectRenamesAndExcludesInheritedTasks() {
        // The project web renames typecheck to tsc. It excludes the global task format and has a local task format.
        assertTrue("tsc" in taskIds("web"))
        assertFalse("typecheck" in taskIds("web"))
        assertEquals("echo", tasks.getValue("web").getAsJsonObject("format").get("command").asString)
        assertEquals(listOf("format", "web"), tasks.getValue("web").getAsJsonObject("format").getAsJsonArray("args").map { it.asString })
    }

    @Test
    fun projectIncludesOnlySomeInheritedTasks() {
        // The project scripts includes only the global task lint.
        assertEquals(setOf("changelog", "lint"), taskIds("scripts"))
    }

    @Test
    fun stacksInheritDifferentTasks() {
        assertTrue("typecheck" in taskIds("admin"))
        assertFalse("audit" in taskIds("web"))
        assertTrue("audit" in taskIds("api"))
        assertFalse("typecheck" in taskIds("api"))
    }

    @Test
    fun projectChangesAnInheritedTask() {
        val args = tasks.getValue("web").getAsJsonObject("lint").getAsJsonArray("args").map { it.asString }
        // The value of mergeArgs is prepend. Thus the arguments of the project are before the arguments of the global task.
        assertEquals(listOf("--max-warnings", "0", "lint"), args)
    }

    /**
     * The commands of the test workspace do not make output files. If a task does not make a necessary output file, moon stops with an error.
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
