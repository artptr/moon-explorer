package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import java.nio.file.Path

/** The saved values of a moon run configuration. */
class MoonRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var target by string()
}

/** A run configuration that runs `moon run <target>` in the workspace root. */
class MoonRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<MoonRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): MoonRunConfigurationOptions = super.getOptions() as MoonRunConfigurationOptions

    /** The task target, for example `web:build`. */
    var target: String
        get() = options.target.orEmpty()
        set(value) {
            options.target = value.trim().ifEmpty { null }
        }

    override fun suggestedName(): String? = target.ifEmpty { null }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = MoonRunConfigurationEditor()

    override fun checkConfiguration() {
        if (target.isEmpty()) throw RuntimeConfigurationError(MoonExplorerBundle.message("run.configuration.error.noTarget"))
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(createCommandLine())
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }

    /**
     * Makes the command line `moon run <target>` in the workspace root.
     * Throws [ExecutionException] with a message for the user if the plugin cannot find the workspace or moon.
     * The function reads the file system. Call this function on a background thread.
     * The platform calls [CommandLineState.startProcess] on a background thread.
     */
    internal fun createCommandLine(): GeneralCommandLine {
        val root = project.basePath?.let { MoonWorkspaceLocator.findWorkspaceRoot(Path.of(it)) }
        return createCommandLine(root) { project.service<MoonExecutableService>().find().executable }
    }

    /** Makes the command line in [workspaceRoot] with the executable from [findExecutable]. Tests give the values. */
    internal fun createCommandLine(workspaceRoot: Path?, findExecutable: () -> Path?): GeneralCommandLine {
        if (target.isEmpty()) throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.noTarget"))
        if (workspaceRoot == null) throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.noWorkspace"))
        val executable = findExecutable()
            ?: throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.moonNotFound"))
        return MoonCli.runTaskCommandLine(executable, workspaceRoot, target)
    }
}
