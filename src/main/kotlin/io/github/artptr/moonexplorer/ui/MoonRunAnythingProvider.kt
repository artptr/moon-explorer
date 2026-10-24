package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.MoonIcons
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.allTasks
import io.github.artptr.moonexplorer.model.loadedTasks
import io.github.artptr.moonexplorer.run.MoonCommandRunner
import io.github.artptr.moonexplorer.run.MoonTaskRunner
import javax.swing.Icon

/**
 * Completes and runs moon commands in **Run Anything**.
 * The completion uses the targets that [MoonTasksService] loaded. It does not start moon.
 * The provider runs `moon run <target>` with a run configuration, the same as the tree. Refer to [MoonTaskRunner.run].
 * The provider runs all other moon commands, for example `moon query projects`, with [MoonCommandRunner].
 * Thus all moon commands use the moon executable that the plugin finds, also if moon is not in `PATH`.
 */
class MoonRunAnythingProvider : RunAnythingCommandLineProvider() {

    /** Runs the target. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var runTask: (Project, String) -> Unit = MoonTaskRunner::run

    /** Runs a moon command that is not `moon run <target>`. Tests replace this value with a test function, because [MoonCommandRunner.run] starts moon. */
    internal var runCommand: (Project, List<String>) -> Unit = { project, args ->
        MoonCommandRunner.run(project, args) { message -> notifyFailure(project, message) }
    }

    override fun getHelpCommand(): String = HELP_COMMAND

    override fun getHelpGroupTitle(): String = MoonExplorerBundle.message("runAnything.group")

    override fun getCompletionGroupTitle(): String = MoonExplorerBundle.message("runAnything.group")

    override fun getHelpCommandPlaceholder(): String = MoonExplorerBundle.message("runAnything.help.placeholder")

    override fun getHelpDescription(): String = MoonExplorerBundle.message("runAnything.help.description")

    override fun getHelpIcon(): Icon = MoonIcons.Moon

    override fun getIcon(value: String): Icon = MoonIcons.Moon

    /**
     * Gives [pattern] if its first word is `moon`. Thus the provider runs all moon commands, but not a command such as `moonx`.
     * If the IDE project has no moon workspace, the function gives null. Then **Run Anything** runs the command in the usual way,
     * for example `moon --version`.
     */
    override fun findMatchingValue(dataContext: DataContext, pattern: String): String? {
        if (ParametersListUtil.parse(pattern).firstOrNull() != HELP_COMMAND) return null
        return pattern.takeIf { hasWorkspace(dataContext) }
    }

    /**
     * Gives all values for the current parameter. The **Run Anything** popup removes the values that do not match the text that the user types.
     * For the first parameter, the function also gives `run <target>` for each target, the same as the Gradle provider.
     * Thus the popup shows the targets also before the user types the space after `run`.
     * If the IDE project has no moon workspace, the function gives no values, because the provider does not run moon commands.
     */
    override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> =
        if (!hasWorkspace(dataContext)) emptySequence() else when (commandLine.completedParameters) {
            emptyList<String>() -> sequenceOf(RUN_COMMAND) + loadedTargets(dataContext).map { "$RUN_COMMAND $it" }
            listOf(RUN_COMMAND) -> loadedTargets(dataContext)
            else -> emptySequence()
        }

    override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val target = targetOf(commandLine.parameters)
        if (target != null) runTask(project, target) else runCommand(project, commandLine.parameters)
        return true
    }

    private fun notifyFailure(project: Project, message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup(COMMAND_GROUP_ID)
            .createNotification(MoonExplorerBundle.message("notification.commandFailed.title"), plainTextHtml(message), NotificationType.ERROR)
            .notify(project)
    }

    /**
     * Tells if the provider runs moon commands for the IDE project of [dataContext]. The value is false only if the project has no moon workspace.
     * While the tasks load, the value is true. The function reads the state of [MoonTasksService]. Thus it does not read files on the EDT.
     */
    private fun hasWorkspace(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        return project.service<MoonTasksService>().state.value != TasksState.NoWorkspace
    }

    private fun loadedTargets(dataContext: DataContext): Sequence<String> {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return emptySequence()
        val tasks = project.service<MoonTasksService>().state.value.loadedTasks ?: return emptySequence()
        return tasks.allTasks.asSequence().map { it.target }
    }

    internal companion object {
        const val HELP_COMMAND = "moon"
        const val RUN_COMMAND = "run"

        /** The ID of the notification group for the errors of moon commands in `plugin.xml`. */
        const val COMMAND_GROUP_ID = "Moon Explorer commands"

        /**
         * Gives the target if [parameters] are `run <target>`. The parameters do not contain `moon`.
         * The function accepts all targets. It also accepts targets that the plugin did not load, for example `:build`.
         * For other parameters, the function gives null.
         * An option is not a target, for example `--help`. Thus the provider runs `moon run --help` with [MoonCommandRunner].
         */
        fun targetOf(parameters: List<String>): String? =
            parameters.takeIf { it.size == 2 && it[0] == RUN_COMMAND && it[1].isNotEmpty() && !it[1].startsWith("-") }?.get(1)
    }
}
