package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.*
import org.junit.Test

class TaskGraphParserTest {

    private val recorded = TestData.recordedTaskGraph()

    @Test
    fun givesTheDirectDependentsOfATarget() {
        assertEquals(setOf("ui:build", "ui:generate-icons"), recorded.dependents["ui:lint"])
        assertEquals(setOf("web:build"), recorded.dependents["web:tsc"])
    }

    @Test
    fun targetWithoutDependentsIsNotInTheMap() {
        assertFalse("web:preview" in recorded.dependents)
    }

    @Test
    fun givesTheMergeStrategiesThatAreNotAppend() {
        assertEquals(mapOf("args" to "prepend", "deps" to "replace"), recorded.mergeStrategies["web:lint"])
        assertFalse("ui:lint" in recorded.mergeStrategies)
    }

    @Test
    fun outputWithoutDataGivesNull() {
        assertNull(TaskGraphParser.parse("{}"))
        assertNull(TaskGraphParser.parse("[]"))
    }

    @Test(expected = MoonOutputException::class)
    fun outputThatIsNotJsonThrows() {
        TaskGraphParser.parse("not JSON")
    }

    @Test
    fun ignoresValuesThatItDoesNotKnow() {
        val json = """
            {"data": {
              "0": 5,
              "1": {"deps": [{"target": "a:b"}]},
              "2": {"target": "c:d", "deps": "x"},
              "3": {"target": "e:f", "deps": [7, {"other": 1}, {"target": "a:b"}], "options": {"mergeArgs": 1, "merge": "x"}}
            }}
        """.trimIndent()
        assertEquals(TaskGraph(mapOf("a:b" to setOf("e:f"))), TaskGraphParser.parse(json))
    }
}
