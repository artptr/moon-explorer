package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import org.junit.Assert
import java.nio.file.Path

class MoonCommandRunnerTest : BasePlatformTestCase() {

    private val root = Path.of("/workspace")
    private val moon = Path.of("/workspace/node_modules/.bin/moon")

    fun testCommandLineRunsMoonWithTheArgumentsInTheWorkspaceRoot() {
        val commandLine = MoonCommandRunner.createCommandLine(listOf("query", "projects"), root) { moon }
        assertEquals(moon.toString(), commandLine.exePath)
        assertEquals(listOf("query", "projects"), commandLine.parametersList.list)
        assertEquals(root, commandLine.workingDirectory)
    }

    fun testCommandLineKeepsTheColorsOfMoon() {
        val commandLine = MoonCommandRunner.createCommandLine(listOf("query", "projects"), root) { moon }
        assertFalse(commandLine.environment.containsKey("NO_COLOR"))
    }

    fun testErrorWithoutAWorkspace() {
        val error = Assert.assertThrows(ExecutionException::class.java) {
            MoonCommandRunner.createCommandLine(listOf("query", "projects"), null) { moon }
        }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.noWorkspace"), error.message)
    }

    fun testErrorWithoutMoon() {
        val error = Assert.assertThrows(ExecutionException::class.java) {
            MoonCommandRunner.createCommandLine(listOf("query", "projects"), root) { null }
        }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.moonNotFound"), error.message)
    }
}
