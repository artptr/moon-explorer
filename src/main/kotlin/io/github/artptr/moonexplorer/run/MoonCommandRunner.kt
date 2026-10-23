package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Runs a moon command without a run configuration, for example `moon query projects`. The Run tool window shows the output.
 * The command uses the same moon executable and the same workspace root as the run configurations.
 */
object MoonCommandRunner {

    /**
     * Runs `moon <args>` in the workspace root. The function finds the workspace root and moon on a background thread.
     * Then it starts moon on the EDT. If the plugin cannot find the workspace or moon, or cannot start moon,
     * the function calls [onFailure] on the EDT with a message for the user.
     */
    fun run(project: Project, args: List<String>, onFailure: (String) -> Unit) {
        val service = project.service<MoonExecutableService>()
        service.scope.launch {
            val commandLine = try {
                withContext(Dispatchers.IO) {
                    val root = service.workspaceRoot()
                    createCommandLine(args, root) { service.findInWorkspace(root).executable }
                }
            } catch (e: ExecutionException) {
                withContext(Dispatchers.EDT) { onFailure(e.message.orEmpty()) }
                return@launch
            }
            withContext(Dispatchers.EDT) {
                try {
                    start(project, commandLine, args)
                } catch (e: ExecutionException) {
                    onFailure(e.message.orEmpty())
                }
            }
        }
    }

    /**
     * Makes the command line `moon <args>` in [workspaceRoot] with the executable from [findExecutable].
     * Throws [ExecutionException] with a message for the user if [workspaceRoot] is null or if [findExecutable] gives null.
     */
    internal fun createCommandLine(args: List<String>, workspaceRoot: Path?, findExecutable: () -> Path?): GeneralCommandLine {
        if (workspaceRoot == null) throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.noWorkspace"))
        val executable = findExecutable()
            ?: throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.moonNotFound"))
        return MoonCli.consoleCommandLine(executable, workspaceRoot, args)
    }

    private fun start(project: Project, commandLine: GeneralCommandLine, args: List<String>) {
        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        RunContentExecutor(project, handler)
            .withTitle((listOf(MOON) + args).joinToString(" "))
            .withActivateToolWindow(true)
            .run()
    }

    private const val MOON = "moon"
}
