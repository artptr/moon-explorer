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
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.targetProjectId
import io.github.artptr.moonexplorer.model.targetTaskId
import java.nio.file.Path

/** The saved values of a moon run configuration. */
class MoonRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var target by string()
    var affected by property(false)
}

/**
 * A run configuration that runs `moon run <targets>` in the workspace root.
 * If [affected] is true, the command also has the option `--affected`.
 */
class MoonRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<MoonRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): MoonRunConfigurationOptions = super.getOptions() as MoonRunConfigurationOptions

    /** One or more task targets with spaces between them, for example `web:build` or `web:build api:build`. */
    var target: String
        get() = options.target.orEmpty()
        set(value) {
            options.target = value.trim().ifEmpty { null }
        }

    /** The task targets in [target]. The list does not contain empty targets, for example from `""` in [target]. */
    var targets: List<String>
        get() = ParametersListUtil.parse(target).filter { it.isNotBlank() }
        set(value) {
            target = ParametersListUtil.join(value)
        }

    /** If true, moon runs only the tasks that the changed files affect. */
    var affected: Boolean
        get() = options.affected
        set(value) {
            options.affected = value
        }

    override fun suggestedName(): String? = targets.takeIf { it.isNotEmpty() }?.let { configurationName(it, affected) }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = MoonRunConfigurationEditor()

    override fun checkConfiguration() {
        if (targets.isEmpty()) throw RuntimeConfigurationError(MoonExplorerBundle.message("run.configuration.error.noTarget"))
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler = startMoonProcess(createCommandLine())
        }

    /**
     * Makes the command line `moon run <targets>` in the workspace root.
     * Throws [ExecutionException] with a message for the user if the plugin cannot find the workspace or moon.
     * The function reads the file system. Call this function on a background thread.
     * The platform calls [CommandLineState.startProcess] on a background thread.
     */
    internal fun createCommandLine(): GeneralCommandLine = moonCommandLine(project, runArgs())

    /** Makes the command line in [workspaceRoot] with the executable from [findExecutable]. Tests give the values. */
    internal fun createCommandLine(workspaceRoot: Path?, findExecutable: () -> Path?): GeneralCommandLine =
        moonCommandLine(runArgs(), workspaceRoot, findExecutable)

    /**
     * Gives the arguments `run <targets>`, and `--affected` after the targets if [affected] is true.
     * Throws [ExecutionException] with a message for the user if the configuration has no target.
     */
    private fun runArgs(): List<String> {
        val targets = targets
        if (targets.isEmpty()) throw ExecutionException(MoonExplorerBundle.message("run.configuration.error.noTarget"))
        // The option --affected can have a value. Thus moon reads a target after --affected as its value. Put the option after the targets.
        return listOf("run") + targets + if (affected) listOf("--affected") else emptyList()
    }
}

/**
 * Gives the name of a run configuration for [targets]. For one target, the name is the target, for example `web:build`.
 * If all targets have a project ID and the same task ID, the name contains the task ID and the project IDs, for example `build (web, api)`.
 * If not, the name contains all targets. Thus two configurations with different targets have different names.
 * If [affected] is true, the name shows that moon runs only the affected tasks.
 */
internal fun configurationName(targets: List<String>, affected: Boolean): String {
    val taskIds = targets.map(::targetTaskId).toSet()
    val name = when {
        targets.size == 1 -> targets.single()
        taskIds.size == 1 && targets.all { ':' in it } -> MoonExplorerBundle.message(
            "run.configuration.name.taskInProjects",
            taskIds.single(),
            targets.joinToString(", ", transform = ::targetProjectId),
        )
        else -> targets.joinToString(", ")
    }
    return if (affected) MoonExplorerBundle.message("run.configuration.name.affected", name) else name
}
