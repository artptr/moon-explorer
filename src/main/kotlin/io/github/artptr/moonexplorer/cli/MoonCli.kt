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

    /** The command did not complete correctly. [message] is the error text from moon. It is not blank. */
    data class Failed(val message: String) : CliResult

    /** The command stopped with the exit code [code], which is not 0. moon gave no error text. */
    data class ExitCode(val code: Int) : CliResult

    /** The moon process stopped before it completed, but not because of the time limit. moon gave no error text. */
    data object Cancelled : CliResult

    /** The plugin cannot start the moon process. [detail] is the error text from the IntelliJ Platform. */
    data class StartFailed(val detail: String) : CliResult

    /** The command did not complete in the time limit. The plugin stopped the moon process. */
    data object TimedOut : CliResult
}

/** Starts the `moon` executable. */
object MoonCli {

    private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /**
     * Runs `moon query <subject>` with [executable] in [workspaceRoot], for example `moon query projects`.
     * In moon v2, the output of `moon query projects` is always JSON.
     * If the caller cancels the coroutine, or if moon does not complete in [timeoutMillis], the function stops the moon process.
     * Call this function on a background thread.
     */
    suspend fun query(executable: Path, workspaceRoot: Path, subject: String, timeoutMillis: Int): CliResult =
        run(executable, workspaceRoot, timeoutMillis, "query", subject)

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

    private suspend fun run(executable: Path, workDir: Path, timeoutMillis: Int, vararg args: String): CliResult {
        val commandLine = commandLine(executable, workDir, *args)
        val handler = try {
            CapturingProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            return CliResult.StartFailed(e.message.orEmpty())
        }
        return runWithTimeout(handler, timeoutMillis)
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
        return when {
            text.isNotEmpty() -> CliResult.Failed(text)
            output.isCancelled -> CliResult.Cancelled
            else -> CliResult.ExitCode(output.exitCode)
        }
    }
}
