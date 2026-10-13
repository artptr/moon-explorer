package io.github.artptr.moonexplorer.cli

import com.intellij.execution.process.ProcessOutput
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonCliTest {

    @Test
    fun exitCodeZeroGivesStdout() {
        assertEquals(CliResult.Success("{}"), MoonCli.toResult(ProcessOutput("{}", "", 0, false, false)))
    }

    @Test
    fun errorGivesStderr() {
        val output = ProcessOutput("", "  Error: config is not valid\n", 1, false, false)
        assertEquals(CliResult.Failed("Error: config is not valid"), MoonCli.toResult(output))
    }

    @Test
    fun errorWithoutStderrGivesStdout() {
        assertEquals(CliResult.Failed("failed"), MoonCli.toResult(ProcessOutput("failed", "", 2, false, false)))
    }

    @Test
    fun errorTextHasNoColorCodes() {
        val output = ProcessOutput("", "\u001B[31mError\u001B[0m: \u001B[1mbad\u001B[0m", 1, false, false)
        assertEquals(CliResult.Failed("Error: bad"), MoonCli.toResult(output))
    }

    @Test
    fun cancelledProcessIsNotSuccess() {
        assertEquals(CliResult.Failed("partial"), MoonCli.toResult(ProcessOutput("partial", "", 0, false, true)))
    }
}
