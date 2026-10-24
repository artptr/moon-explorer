package io.github.artptr.moonexplorer.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

/** The errors and the colors of [moonCommandLine] are also in `MoonRunConfigurationTest`, because the run configurations use the same function. */
class MoonCommandLineTest : BasePlatformTestCase() {

    fun testCommandLineRunsMoonWithTheArgumentsInTheWorkspaceRoot() {
        val root = Path.of("/workspace")
        val moon = Path.of("/workspace/node_modules/.bin/moon")
        val commandLine = moonCommandLine(listOf("query", "projects"), root) { moon }
        assertEquals(moon.toString(), commandLine.exePath)
        assertEquals(listOf("query", "projects"), commandLine.parametersList.list)
        assertEquals(root, commandLine.workingDirectory)
    }
}
