package io.github.artptr.moonexplorer.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunDialog
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle

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

    /**
     * Opens the run configuration of [target] in the configuration dialog with [edit]. Call this function on the EDT.
     * The function uses only a configuration without the option --affected, the same as [run] for one target.
     * Thus the dialog shows the configuration that the next run of [target] uses.
     * If there is no configuration for [target] without the option --affected, the dialog shows a new configuration.
     * If the user clicks OK, the function saves the configuration and selects it. A temporary configuration becomes a saved configuration.
     * If the user clicks Cancel, the function does not add a configuration.
     * Tests replace [edit] with a test function, because [RunDialog] shows a dialog.
     */
    fun editConfiguration(
        project: Project,
        target: String,
        edit: (Project, RunnerAndConfigurationSettings, String) -> Boolean = RunDialog::editConfiguration,
    ) {
        val runManager = RunManager.getInstance(project)
        val targets = listOf(target)
        val existing = find(project, targets, affected = false)
        val settings = existing ?: create(project, targets, affected = false)
        if (!edit(project, settings, MoonExplorerBundle.message("run.configuration.edit.title"))) return
        when {
            existing == null -> runManager.addConfiguration(settings)
            settings.isTemporary -> runManager.makeStable(settings)
        }
        runManager.selectedConfiguration = settings
    }

    /**
     * Gives the run configuration for [targets] and [affected], or null.
     * The function uses a configuration only if it has the same targets in the same sequence and the same [affected] value.
     * The function does not compare the arguments, the force option, or the environment variables.
     * If more than one configuration has the same targets and the same [affected] value,
     * the function gives a saved configuration, not a temporary configuration.
     * If there is more than one saved configuration, the function gives the first saved configuration in the list.
     */
    internal fun find(project: Project, targets: List<String>, affected: Boolean): RunnerAndConfigurationSettings? =
        RunManager.getInstance(project).getConfigurationSettingsList(MoonRunConfigurationType.getInstance())
            .filter {
                val configuration = it.configuration
                configuration is MoonRunConfiguration && configuration.runs(targets, affected)
            }
            .let { found -> found.firstOrNull { !it.isTemporary } ?: found.firstOrNull() }

    /**
     * Gives the run configuration for [targets] and [affected] from [find], or makes a new one. The function also selects it.
     * Thus a run from the tree uses the arguments, the force option, and the environment variables of a saved configuration.
     */
    internal fun findOrCreate(
        project: Project,
        targets: List<String>,
        affected: Boolean,
        temporary: Boolean,
    ): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val settings = find(project, targets, affected)
            ?: create(project, targets, affected).also { created ->
                if (temporary) runManager.setTemporaryConfiguration(created) else runManager.addConfiguration(created)
            }
        runManager.selectedConfiguration = settings
        return settings
    }

    /** Makes a new run configuration for [targets] and [affected]. The function does not add it to the run manager. */
    private fun create(project: Project, targets: List<String>, affected: Boolean): RunnerAndConfigurationSettings {
        val factory = MoonRunConfigurationType.getInstance().factory
        val settings = RunManager.getInstance(project).createConfiguration(configurationName(targets, affected), factory)
        (settings.configuration as MoonRunConfiguration).setUp(targets, affected)
        return settings
    }
}
