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
    fun run(project: Project, target: String) {
        val settings = findOrCreate(project, target, temporary = true)
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    /** Saves a run configuration for [target] and selects it. If there is a temporary configuration, the function saves it. */
    fun createConfiguration(project: Project, target: String): RunnerAndConfigurationSettings {
        val settings = findOrCreate(project, target, temporary = false)
        if (settings.isTemporary) RunManager.getInstance(project).makeStable(settings)
        return settings
    }

    /** Gives the run configuration for [target], or makes a new one. The function also selects it. */
    internal fun findOrCreate(project: Project, target: String, temporary: Boolean): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val type = MoonRunConfigurationType.getInstance()
        val settings = runManager.getConfigurationSettingsList(type)
            .firstOrNull { (it.configuration as? MoonRunConfiguration)?.target == target }
            ?: runManager.createConfiguration(target, type.factory).also { created ->
                (created.configuration as MoonRunConfiguration).target = target
                if (temporary) runManager.setTemporaryConfiguration(created) else runManager.addConfiguration(created)
            }
        runManager.selectedConfiguration = settings
        return settings
    }
}
