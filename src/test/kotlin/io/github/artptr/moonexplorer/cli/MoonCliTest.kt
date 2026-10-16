package io.github.artptr.moonexplorer.cli

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.Platform
import com.intellij.execution.process.ProcessOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

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

    @Test
    fun errorWithoutTextGivesTheExitCode() {
        assertEquals(CliResult.Failed("The moon process stopped with exit code 3."), MoonCli.toResult(ProcessOutput("", " ", 3, false, false)))
    }

    @Test
    fun timeoutGivesTimedOut() {
        assertEquals(CliResult.TimedOut, MoonCli.toResult(ProcessOutput("", "", -1, true, false)))
    }

    @Test
    fun timeoutWithTextGivesTimedOut() {
        assertEquals(CliResult.TimedOut, MoonCli.toResult(ProcessOutput("partial", "Downloading", 143, true, false)))
    }

    @Test
    fun commandLineRunsTheExecutableInTheWorkDirectoryWithoutColors() {
        val commandLine = MoonCli.commandLine(Path.of("/tools/moon"), Path.of("/work"), "query", "tasks")
        assertEquals(Path.of("/tools/moon").toString(), commandLine.exePath)
        assertEquals(listOf("query", "tasks"), commandLine.parametersList.list)
        assertEquals(Path.of("/work"), commandLine.workingDirectory)
        assertEquals("1", commandLine.environment["NO_COLOR"])
    }

    @Test
    fun windowsEscapesTheArgumentsOfMoonCmdForCmdExe() {
        // Windows starts a .cmd file through cmd.exe. CommandLineUtil escapes the arguments of a .cmd file for cmd.exe,
        // but not the arguments of a .exe file. Thus an argument with a special character of cmd.exe stays one argument.
        fun windowsCommandLine(name: String): List<String> {
            val commandLine = MoonCli.commandLine(Path.of("C:/work/node_modules/.bin/$name"), Path.of("C:/work"), "run", "a&b")
            return CommandLineUtil.toCommandLine(commandLine.exePath, commandLine.parametersList.list, Platform.WINDOWS)
        }
        val cmd = windowsCommandLine("moon.cmd")
        val exe = windowsCommandLine("moon.exe")
        assertTrue("Command line: $cmd", cmd.first().endsWith("moon.cmd"))
        assertEquals("a&b", exe.last())
        assertTrue("The argument for cmd.exe must be escaped: $cmd", cmd.last() != "a&b")
    }
}
