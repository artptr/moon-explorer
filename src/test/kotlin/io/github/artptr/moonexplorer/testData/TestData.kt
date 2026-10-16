package io.github.artptr.moonexplorer.testData

import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import java.io.File

/** Paths to the test data in `src/test/testData/`. Refer to `docs/development.md`. */
object TestData {
    /** Gradle sets the property `moonExplorer.testData`. Without it, the path is relative to the repository root. */
    private val root = File(System.getProperty("moonExplorer.testData") ?: "src/test/testData")

    /** The test workspace with sample data. */
    val workspace = File(root, "workspace")

    /** The recorded moon CLI output for the test workspace. */
    val moon = File(root, "moon")

    /** Reads a recorded file from [moon]. */
    fun recorded(name: String): String = File(moon, name).readText()

    /** The tasks of the recorded `moon query tasks` output, with the stacks of the recorded `moon query projects` output. */
    fun recordedTasks(): WorkspaceTasks =
        QueryTasksParser.parse(recorded("query-tasks.json")).withStacks(QueryProjectsParser.parseStacks(recorded("query-projects.json")))
}
