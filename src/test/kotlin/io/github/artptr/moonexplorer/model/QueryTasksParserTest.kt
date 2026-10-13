package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTasksParserTest {

    private val recorded = QueryTasksParser.parse(TestData.recorded("query-tasks.json"))

    private fun project(id: String) = recorded.projects.single { it.id == id }

    @Test
    fun recordedOutputHasAllProjectsInOrder() {
        assertEquals(listOf("admin", "api", "db", "ui", "utils", "web", "worker"), recorded.projects.map { it.id })
    }

    @Test
    fun recordedOutputHasTheTasksOfAProjectInOrder() {
        assertEquals(
            listOf("build", "dev", "format", "lint", "preview", "test", "typecheck"),
            project("web").tasks.map { it.id },
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
    fun sortsProjectsAndTasksById() {
        val json = """
            {"tasks": {
              "zeta": {"b": {"id": "b"}, "A": {"id": "A"}, "c": {"id": "c"}},
              "Beta": {"x": {"id": "x"}},
              "alpha": {"y": {"id": "y", "description": "  "}}
            }}
        """
        val parsed = QueryTasksParser.parse(json)
        assertEquals(listOf("alpha", "Beta", "zeta"), parsed.projects.map { it.id })
        assertEquals(listOf("A", "b", "c"), parsed.projects.last().tasks.map { it.id })
        assertNull("A blank description is null", parsed.projects.first().tasks.single().description)
        assertEquals("zeta:b", parsed.projects.last().tasks[1].target)
    }

    @Test
    fun emptyWorkspaceHasNoProjects() {
        assertTrue(QueryTasksParser.parse("""{"tasks": {}, "options": {}}""").projects.isEmpty())
    }

    @Test(expected = MoonOutputException::class)
    fun outputThatIsNotJsonThrows() {
        QueryTasksParser.parse("Error: not JSON {")
    }

    @Test(expected = MoonOutputException::class)
    fun outputWithoutTasksThrows() {
        QueryTasksParser.parse("""{"projects": []}""")
    }
}
