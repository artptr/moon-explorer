package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import java.nio.file.Path

/**
 * Makes the command line `moon <args>` in the workspace root of [project]. The run configurations and [MoonCommandRunner] use it.
 * Thus all moon commands of the user use the same moon executable and the same workspace root.
 * Throws [ExecutionException] with a message for the user if the plugin cannot find the workspace or moon.
 * The function reads the file system. Call this function on a background thread.
 */
internal fun moonCommandLine(project: Project, args: List<String>): GeneralCommandLine {
    val service = project.service<MoonExecutableService>()
    val root = service.workspaceRoot()
    return moonCommandLine(args, root) { service.findInWorkspace(root).executable }
}

/** Makes the command line `moon <args>` in [workspaceRoot] with the executable from [findExecutable]. Tests give the values. */
internal fun moonCommandLine(args: List<String>, workspaceRoot: Path?, findExecutable: () -> Path?): GeneralCommandLine {
    if (workspaceRoot == null) throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.noWorkspace"))
    val executable = findExecutable()
        ?: throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.moonNotFound"))
    return MoonCli.consoleCommandLine(executable, workspaceRoot, args)
}

/** Starts [commandLine]. The user can stop the process. When the process stops, the console shows its exit code. */
internal fun startMoonProcess(commandLine: GeneralCommandLine): ProcessHandler =
    KillableColoredProcessHandler(commandLine).also(ProcessTerminatedListener::attach)
