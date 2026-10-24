package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a moon command without a run configuration, for example `moon query projects`. The Run tool window shows the output.
 * The command uses the same moon executable and the same workspace root as the run configurations. Refer to [moonCommandLine].
 */
object MoonCommandRunner {

    /**
     * Runs `moon <args>` in the workspace root. The function finds the workspace root and moon on a background thread.
     * Then it starts moon on the EDT. If the plugin cannot find the workspace or moon, or cannot start moon,
     * the function calls [onFailure] on the EDT with a message for the user.
     */
    fun run(project: Project, args: List<String>, onFailure: (String) -> Unit) {
        project.service<MoonExecutableService>().scope.launch {
            try {
                val commandLine = withContext(Dispatchers.IO) { moonCommandLine(project, args) }
                withContext(Dispatchers.EDT) {
                    RunContentExecutor(project, startMoonProcess(commandLine))
                        .withTitle((listOf("moon") + args).joinToString(" "))
                        .withActivateToolWindow(true)
                        .run()
                }
            } catch (e: ExecutionException) {
                withContext(Dispatchers.EDT) { onFailure(e.message.orEmpty()) }
            }
        }
    }
}
