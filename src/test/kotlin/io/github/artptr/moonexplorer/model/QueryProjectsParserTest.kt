package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryProjectsParserTest {

    private val recorded = QueryProjectsParser.parse(TestData.recorded("query-projects.json"))

    private fun project(id: String) = recorded.projects.single { it.id == id }

    /** Gives the value of [category] for each project in [json]. */
    private fun values(json: String, category: TaskCategory) = QueryProjectsParser.parse(json).projects.associate { it.id to category.valueFor(it) }

    @Test
    fun recordedOutputHasAllProjectsWithTasksInOrder() {
        // The project config has no tasks. Thus it is not in the result.
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), recorded.projects.map { it.id })
    }

    @Test
    fun recordedOutputHasTheSourceOfAllProjectsInOrder() {
        // The project config has no tasks. Its source is also in the result.
        val expected = listOf(
            ProjectSource("admin", "apps/admin"),
            ProjectSource("api", "apps/api"),
            ProjectSource("config", "packages/config"),
            ProjectSource("db", "packages/db"),
            ProjectSource("icons", "packages/ui/icons"),
            ProjectSource("scripts", "tools/scripts"),
            ProjectSource("ui", "packages/ui"),
            ProjectSource("utils", "packages/utils"),
            ProjectSource("web", "apps/web"),
            ProjectSource("worker", "apps/worker"),
        )
        assertEquals(expected, recorded.sources)
    }

    @Test
    fun projectsWithoutIdOrSourceHaveNoSource() {
        val json = """
            {"projects": [
              {"id": "a", "source": "apps/a"}, {"source": "apps/b"}, {"id": "c"}, {"id": "d", "source": 5}
            ]}
        """
        assertEquals(listOf(ProjectSource("a", "apps/a")), QueryProjectsParser.parse(json).sources)
    }

    @Test
    fun recordedOutputHasTheTaskConfigsOfAProject() {
        // The project web changes the inherited task lint in its moon.yml. Thus lint is in the two lists.
        val configs = recorded.taskConfigs.getValue("web")
        assertEquals(setOf("build", "dev", "lint", "preview"), configs.ownTasks)
        assertEquals(listOf(".moon/tasks/all.yml"), configs.layers["lint"])
        assertEquals(listOf(".moon/tasks/frontend.yml"), configs.layers["test"])
        assertNull(configs.layers["build"])
    }

    @Test
    fun recordedOutputHasTheTaskConfigsOfAProjectWithoutTasks() {
        val configs = recorded.taskConfigs.getValue("config")
        assertEquals(emptySet<String>(), configs.ownTasks)
        assertEquals(setOf("format", "lint"), configs.layers.keys)
    }

    @Test
    fun taskConfigsIgnoreValuesThatAreNotStrings() {
        val json = """
            {"projects": [
              {"id": "a", "config": {"tasks": {"build": {}}}, "inherited": {"layers": {"lint": [".moon/tasks/all.yml", 5], "test": "x", "e": []}}},
              {"id": "b", "config": {"tasks": []}, "inherited": 5}
            ]}
        """
        val configs = QueryProjectsParser.parse(json).taskConfigs
        assertEquals(TaskConfigs(setOf("build"), mapOf("lint" to listOf(".moon/tasks/all.yml"))), configs["a"])
        assertNull(configs["b"])
    }

    @Test
    fun recordedOutputHasTheTasksOfAProjectInOrder() {
        assertEquals(
            listOf("build", "dev", "format", "lint", "preview", "test", "typecheck"),
            project("web").tasks.map { it.id },
        )
    }

    @Test
    fun internalTaskIsNotInTheResult() {
        // moon does not run internal tasks directly. Thus the tree must not show them.
        assertEquals(
            listOf("build", "format", "lint", "storybook", "test", "typecheck"),
            project("ui").tasks.map { it.id },
        )
    }

    @Test
    fun taskHasTargetAndDescription() {
        val build = project("web").tasks.single { it.id == "build" }
        assertEquals("web:build", build.target)
        assertEquals("Make the production bundle.", build.description)
    }

    @Test
    fun taskWithoutDescriptionHasNull() {
        assertNull(project("web").tasks.single { it.id == "format" }.description)
    }

    @Test
    fun recordedOutputHasTheStackOfEachProject() {
        val expected = mapOf(
            "admin" to "frontend",
            "api" to "backend",
            "db" to "backend",
            "icons" to "frontend",
            "scripts" to null,
            "ui" to "frontend",
            "utils" to "frontend",
            "web" to "frontend",
            "worker" to "backend",
        )
        assertEquals(expected, recorded.projects.associate { it.id to it.stack })
    }

    @Test
    fun sortsProjectsAndTasksById() {
        val json = """
            {"projects": [
              {"id": "zeta", "tasks": {"b": {"id": "b"}, "A": {"id": "A"}, "c": {"id": "c"}}},
              {"id": "Beta", "tasks": {"x": {"id": "x"}}},
              {"id": "alpha", "tasks": {"y": {"id": "y", "description": "  "}}}
            ]}
        """
        val parsed = QueryProjectsParser.parse(json)
        assertEquals(listOf("alpha", "Beta", "zeta"), parsed.projects.map { it.id })
        assertEquals(listOf("A", "b", "c"), parsed.projects.last().tasks.map { it.id })
        assertNull("A blank description is null", parsed.projects.first().tasks.single().description)
        assertEquals("zeta:b", parsed.projects.last().tasks[1].target)
    }

    @Test
    fun emptyWorkspaceHasNoProjects() {
        assertTrue(QueryProjectsParser.parse("""{"projects": [], "options": {}}""").projects.isEmpty())
    }

    @Test
    fun outputThatIsNotJsonThrows() {
        val e = assertThrows(MoonOutputException::class.java) { QueryProjectsParser.parse("Error: not JSON {") }
        assertTrue("Problem: ${e.problem}", e.problem is OutputProblem.NotJson)
    }

    @Test
    fun outputWithoutProjectsThrows() {
        val e = assertThrows(MoonOutputException::class.java) { QueryProjectsParser.parse("""{"tasks": {}}""") }
        assertEquals(OutputProblem.NoProjects, e.problem)
    }

    @Test
    fun valuesThatAreNotStringsAreIgnored() {
        val json = """{"projects": [{"id": "p", "tasks": {"t": {"id": 5, "description": true}}}]}"""
        val task = QueryProjectsParser.parse(json).projects.single().tasks.single()
        assertEquals("t", task.id)
        assertEquals("p:t", task.target)
        assertNull(task.description)
    }

    @Test
    fun taskWithoutIdHasTheKeyAsId() {
        val task = QueryProjectsParser.parse("""{"projects": [{"id": "p", "tasks": {"t": {"target": "p:t"}}}]}""").projects.single().tasks.single()
        assertEquals("t", task.id)
        assertEquals("p:t", task.target)
    }

    @Test
    fun taskWithoutTargetHasTheTargetOfProjectAndId() {
        val task = QueryProjectsParser.parse("""{"projects": [{"id": "p", "tasks": {"key": {"id": "t"}}}]}""").projects.single().tasks.single()
        assertEquals("t", task.id)
        assertEquals("p:t", task.target)
    }

    @Test
    fun taskValuesThatAreNotObjectsAreIgnored() {
        val json = """{"projects": [{"id": "p", "tasks": {"a": 5, "b": null, "c": "text", "d": [], "e": {"id": "e"}}}]}"""
        assertEquals(listOf("e"), QueryProjectsParser.parse(json).projects.single().tasks.map { it.id })
    }

    @Test
    fun projectWithOnlyTaskValuesThatAreNotObjectsIsNotInTheResult() {
        val json = """{"projects": [{"id": "a", "tasks": {"t": 5}}, {"id": "b", "tasks": [{"id": "t"}]}]}"""
        assertTrue(QueryProjectsParser.parse(json).projects.isEmpty())
    }

    @Test
    fun projectsWithoutTasksAreNotInTheResult() {
        val json = """
            {"projects": [
              {"id": "a", "tasks": {}}, {"id": "b", "tasks": null}, {"id": "c", "tasks": {"t": {"id": "t"}}}, {"id": "d"}
            ]}
        """
        assertEquals(listOf("c"), QueryProjectsParser.parse(json).projects.map { it.id })
    }

    @Test
    fun projectsWithoutIdAreIgnored() {
        val json = """{"projects": [{"tasks": {"t": {"id": "t"}}}, 5, {"id": "b", "tasks": {"t": {"id": "t"}}}]}"""
        assertEquals(listOf("b"), QueryProjectsParser.parse(json).projects.map { it.id })
    }

    @Test
    fun unknownStackIsNull() {
        // moon gives "unknown" if the project does not set stack.
        val json = """{"projects": [{"id": "a", "stack": "unknown", "tasks": {"t": {"id": "t"}}}]}"""
        assertEquals(mapOf("a" to null), values(json, TaskCategory.STACK))
    }

    @Test
    fun unknownStackIsNullInAllCases() {
        val json = """
            {"projects": [
              {"id": "a", "stack": "Unknown", "tasks": {"t": {"id": "t"}}}, {"id": "b", "stack": "UNKNOWN", "tasks": {"t": {"id": "t"}}}
            ]}
        """
        assertEquals(mapOf("a" to null, "b" to null), values(json, TaskCategory.STACK))
    }

    @Test
    fun stackComesFromTheConfigIfTheTopLevelValueIsMissing() {
        val json = """
            {"projects": [
              {"id": "a", "config": {"stack": "infrastructure"}, "tasks": {"t": {"id": "t"}}}, {"id": "b", "stack": "", "tasks": {"t": {"id": "t"}}}
            ]}
        """
        assertEquals(mapOf("a" to "infrastructure", "b" to null), values(json, TaskCategory.STACK))
    }

    @Test
    fun recordedOutputHasTheLayerOfEachProject() {
        val expected = mapOf(
            "admin" to "application",
            "api" to "application",
            "db" to "library",
            "icons" to "library",
            "scripts" to null,
            "ui" to "library",
            "utils" to "library",
            "web" to "application",
            "worker" to "application",
        )
        assertEquals(expected, recorded.projects.associate { it.id to it.layer })
    }

    @Test
    fun unknownOrEmptyLayerIsNull() {
        // moon gives "unknown" if the project does not set layer.
        val json = """
            {"projects": [
              {"id": "a", "layer": "unknown", "tasks": {"t": {"id": "t"}}}, {"id": "b", "layer": "", "tasks": {"t": {"id": "t"}}},
              {"id": "c", "tasks": {"t": {"id": "t"}}}
            ]}
        """
        assertEquals(mapOf("a" to null, "b" to null, "c" to null), values(json, TaskCategory.LAYER))
    }

    @Test
    fun layerComesFromTheConfigIfTheTopLevelValueIsMissing() {
        val json = """{"projects": [{"id": "a", "config": {"layer": "tool"}, "tasks": {"t": {"id": "t"}}}]}"""
        assertEquals(mapOf("a" to "tool"), values(json, TaskCategory.LAYER))
    }
}
