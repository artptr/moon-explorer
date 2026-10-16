package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryProjectsParserTest {

    private val recorded = QueryProjectsParser.parseStacks(TestData.recorded("query-projects.json"))

    @Test
    fun recordedOutputHasTheStackOfEachProject() {
        val expected = mapOf(
            "admin" to "frontend",
            "api" to "backend",
            "config" to null,
            "db" to "backend",
            "ui" to "frontend",
            "utils" to "frontend",
            "web" to "frontend",
            "worker" to "backend",
        )
        assertEquals(expected, recorded)
    }

    @Test
    fun unknownStackIsNull() {
        // moon gives "unknown" if the project does not set stack. The project config does not set stack.
        assertNull(recorded.getValue("config"))
    }

    @Test
    fun stackComesFromTheConfigIfTheTopLevelValueIsMissing() {
        val json = """{"projects": [{"id": "a", "config": {"stack": "infrastructure"}}, {"id": "b", "stack": ""}]}"""
        assertEquals(mapOf("a" to "infrastructure", "b" to null), QueryProjectsParser.parseStacks(json))
    }

    @Test
    fun projectsWithoutIdAreIgnored() {
        assertEquals(mapOf("b" to "data"), QueryProjectsParser.parseStacks("""{"projects": [{"stack": "x"}, 5, {"id": "b", "stack": "data"}]}"""))
    }

    @Test(expected = MoonOutputException::class)
    fun outputThatIsNotJsonThrows() {
        QueryProjectsParser.parseStacks("Error {")
    }

    @Test(expected = MoonOutputException::class)
    fun outputWithoutProjectsThrows() {
        QueryProjectsParser.parseStacks("""{"tasks": {}}""")
    }

    @Test
    fun unknownStackIsNullInAllCases() {
        val json = """{"projects": [{"id": "a", "stack": "Unknown"}, {"id": "b", "stack": "UNKNOWN"}]}"""
        assertEquals(mapOf("a" to null, "b" to null), QueryProjectsParser.parseStacks(json))
    }
}
