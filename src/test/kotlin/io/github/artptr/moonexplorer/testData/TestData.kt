package io.github.artptr.moonexplorer.testData

import java.io.File

/** Paths to the test data in `src/test/testData/`. Refer to `docs/development.md`. */
object TestData {
    private val root = File("src/test/testData")

    /** The test workspace with sample data. */
    val workspace = File(root, "workspace")

    /** The recorded moon CLI output for the test workspace. */
    val moon = File(root, "moon")

    /** Reads a recorded file from [moon]. */
    fun recorded(name: String): String = File(moon, name).readText()
}
