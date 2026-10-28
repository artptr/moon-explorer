package io.github.artptr.moonexplorer.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project

/** Makes and runs moon run configurations for the tasks in the tool window. */
object MoonTaskRunner {

    /**
     * Runs [target] with a temporary run configuration.
     * If there is a run configuration for [target], the function uses it.
     */
    fun run(project: Project, target: String) = run(project, listOf(target))

    /**
     * Runs [targets] with one temporary run configuration. If [affected] is true, moon runs only the affected tasks.
     * If there is a run configuration with the same targets and the same [affected] value, the function uses it.
     */
    fun run(project: Project, targets: List<String>, affected: Boolean = false) {
        val settings = findOrCreate(project, targets, affected, temporary = true)
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    /** Saves a run configuration for [target] and selects it. If there is a temporary configuration, the function saves it. */
    fun createConfiguration(project: Project, target: String): RunnerAndConfigurationSettings {
        val settings = findOrCreate(project, listOf(target), affected = false, temporary = false)
        if (settings.isTemporary) RunManager.getInstance(project).makeStable(settings)
        return settings
    }

    /**
     * Gives the run configuration for [targets] and [affected], or makes a new one. The function also selects it.
     * The function uses a configuration only if it has the same targets in the same sequence and the same [affected] value.
     * The function does not compare the arguments, the force option, or the environment variables.
     * Thus a run from the tree uses these values of a saved configuration.
     */
    internal fun findOrCreate(
        project: Project,
        targets: List<String>,
        affected: Boolean,
        temporary: Boolean,
    ): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val type = MoonRunConfigurationType.getInstance()
        val settings = runManager.getConfigurationSettingsList(type)
            .firstOrNull {
                val configuration = it.configuration
                configuration is MoonRunConfiguration && configuration.runs(targets, affected)
            }
            ?: runManager.createConfiguration(configurationName(targets, affected), type.factory).also { created ->
                (created.configuration as MoonRunConfiguration).setUp(targets, affected)
                if (temporary) runManager.setTemporaryConfiguration(created) else runManager.addConfiguration(created)
            }
        runManager.selectedConfiguration = settings
        return settings
    }
}
