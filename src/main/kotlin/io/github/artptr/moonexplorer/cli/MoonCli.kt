package io.github.artptr.moonexplorer.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.coroutineToIndicator
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** The result of a moon CLI command. */
sealed interface CliResult {
    /** The command completed with exit code 0. */
    data class Success(val stdout: String) : CliResult

    /** The plugin cannot find the `moon` executable. [examined] contains the files that the plugin examined before `PATH`. */
    data class NotFound(val examined: List<Path>) : CliResult

    /** The command did not complete correctly. [message] is the error text from moon. */
    data class Failed(val message: String) : CliResult

    /** The command did not complete in the time limit. The plugin stopped the moon process. */
    data object TimedOut : CliResult
}

/** Starts the `moon` executable. */
object MoonCli {

    /** The time limit for a moon command. After this time, the plugin stops the moon process. */
    internal const val TIMEOUT_MILLIS = 60_000

    private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /**
     * Runs `moon query tasks` in [workspaceRoot].
     * In moon v2, the output of this command is always JSON.
     * [settingsPath] is the path to moon from the plugin settings. Refer to [MoonExecutableLocator.find].
     * If the caller cancels the coroutine, or if moon does not complete in [TIMEOUT_MILLIS], the function stops the moon process.
     * Call this function on a background thread.
     */
    suspend fun queryTasks(workspaceRoot: Path, settingsPath: String?): CliResult =
        run(workspaceRoot, settingsPath, "query", "tasks")

    /**
     * Runs `moon query projects` in [workspaceRoot]. The output is JSON.
     * The function obeys the same rules as [queryTasks].
     */
    suspend fun queryProjects(workspaceRoot: Path, settingsPath: String?): CliResult =
        run(workspaceRoot, settingsPath, "query", "projects")

    /**
     * Makes the command line `moon run <target>` in [workspaceRoot]. The run configurations use this command line.
     * The output keeps its colors, because the console of the Run tool window shows them.
     */
    fun runTaskCommandLine(executable: Path, workspaceRoot: Path, target: String): GeneralCommandLine =
        baseCommandLine(executable, workspaceRoot, "run", target)

    /**
     * Makes the command line that runs [executable] with [args] in [workDir]. The output of this command line has no colors.
     * The plugin reads the output of this command line.
     */
    internal fun commandLine(executable: Path, workDir: Path, vararg args: String): GeneralCommandLine =
        baseCommandLine(executable, workDir, *args).withEnvironment("NO_COLOR", "1")

    /** Makes a command line that uses the environment of the login shell. */
    private fun baseCommandLine(executable: Path, workDir: Path, vararg args: String): GeneralCommandLine =
        GeneralCommandLine(executable.toString(), *args)
            .withWorkingDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(StandardCharsets.UTF_8)

    private suspend fun run(workDir: Path, settingsPath: String?, vararg args: String): CliResult {
        val search = MoonExecutableLocator.find(settingsPath, workDir)
        val executable = search.executable ?: return CliResult.NotFound(search.examined)
        val commandLine = commandLine(executable, workDir, *args)
        val handler = try {
            CapturingProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            return CliResult.Failed(e.message.orEmpty())
        }
        return runWithTimeout(handler, TIMEOUT_MILLIS)
    }

    /**
     * Waits until the process of [handler] stops and gives the result.
     * If the process does not stop in [timeoutMillis], the function stops the process.
     */
    internal suspend fun runWithTimeout(handler: CapturingProcessHandler, timeoutMillis: Int): CliResult {
        val output = coroutineToIndicator { indicator -> handler.runProcessWithProgressIndicator(indicator, timeoutMillis, true) }
        return toResult(output)
    }

    /** Converts the output of a moon process to a [CliResult]. */
    internal fun toResult(output: ProcessOutput): CliResult {
        if (output.isTimeout) return CliResult.TimedOut
        if (output.exitCode == 0 && !output.isCancelled) return CliResult.Success(output.stdout)
        val text = output.stderr.ifBlank { output.stdout }.replace(ANSI_ESCAPE, "").trim()
        return CliResult.Failed(text.ifEmpty { statusText(output) })
    }

    /** Tells why the process stopped, if moon did not write an error text. */
    private fun statusText(output: ProcessOutput): String = when {
        output.isCancelled -> "The moon process was stopped."
        else -> "The moon process stopped with exit code ${output.exitCode}."
    }
}
