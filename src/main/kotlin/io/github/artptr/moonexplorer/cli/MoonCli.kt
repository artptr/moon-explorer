package io.github.artptr.moonexplorer.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.coroutineToIndicator
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** The result of a moon CLI command. */
sealed interface CliResult {
    /** The command completed with exit code 0. */
    data class Success(val stdout: String) : CliResult

    /** The plugin cannot find the `moon` executable. */
    data object NotFound : CliResult

    /** The command did not complete correctly. [message] is the error text from moon. */
    data class Failed(val message: String) : CliResult

    /** The command did not complete in the time limit. The plugin stopped the moon process. */
    data object TimedOut : CliResult
}

/** Starts the `moon` executable. */
object MoonCli {

    private const val EXECUTABLE = "moon"

    /** The time limit for a moon command. After this time, the plugin stops the moon process. */
    internal const val TIMEOUT_MILLIS = 60_000

    private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /**
     * Runs `moon query tasks` in [workspaceRoot].
     * In moon v2, the output of this command is always JSON.
     * If the caller cancels the coroutine, or if moon does not complete in [TIMEOUT_MILLIS], the function stops the moon process.
     * Call this function on a background thread.
     */
    suspend fun queryTasks(workspaceRoot: Path): CliResult = run(workspaceRoot, "query", "tasks")

    private suspend fun run(workDir: Path, vararg args: String): CliResult {
        // The plugin uses moon from PATH until the setting for the path to moon is available.
        val executable = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(EXECUTABLE) ?: return CliResult.NotFound
        val commandLine = GeneralCommandLine(executable.path, *args)
            .withWorkingDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment("NO_COLOR", "1")
            .withCharset(StandardCharsets.UTF_8)
        val handler = try {
            CapturingProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            return CliResult.Failed(e.message.orEmpty())
        }
        return run(handler, TIMEOUT_MILLIS)
    }

    /**
     * Waits until the process of [handler] stops and gives the result.
     * If the process does not stop in [timeoutMillis], the function stops the process. Tests use a fake process.
     */
    internal suspend fun run(handler: CapturingProcessHandler, timeoutMillis: Int): CliResult {
        val output = coroutineToIndicator { indicator -> handler.runProcessWithProgressIndicator(indicator, timeoutMillis, true) }
        return toResult(output)
    }

    /** Converts the output of a moon process to a [CliResult]. */
    internal fun toResult(output: ProcessOutput): CliResult {
        if (output.isTimeout) return CliResult.TimedOut
        if (output.exitCode == 0 && !output.isTimeout && !output.isCancelled) return CliResult.Success(output.stdout)
        val text = output.stderr.ifBlank { output.stdout }.replace(ANSI_ESCAPE, "").trim()
        return CliResult.Failed(text.ifEmpty { statusText(output) })
    }

    /** Tells why the process stopped, if moon did not write an error text. */
    private fun statusText(output: ProcessOutput): String = when {
        output.isCancelled -> "The moon process was stopped."
        else -> "The moon process stopped with exit code ${output.exitCode}."
    }
}
