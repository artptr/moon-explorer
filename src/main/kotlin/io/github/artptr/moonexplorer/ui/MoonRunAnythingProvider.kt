package io.github.artptr.moonexplorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.allTasks
import io.github.artptr.moonexplorer.model.loadedTasks
import io.github.artptr.moonexplorer.run.MoonTaskRunner
import javax.swing.Icon

/**
 * Completes and runs `moon run <target>` in **Run Anything**.
 * The completion uses the targets that [MoonTasksService] loaded. It does not start moon.
 * The provider runs the target with a run configuration, the same as the tree. Refer to [MoonTaskRunner.run].
 * The provider does not run other moon commands, for example `moon query projects`. **Run Anything** runs these commands as usual.
 */
class MoonRunAnythingProvider : RunAnythingCommandLineProvider() {

    /** Runs the target. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var runTask: (Project, String) -> Unit = MoonTaskRunner::run

    override fun getHelpCommand(): String = HELP_COMMAND

    override fun getHelpGroupTitle(): String = MoonExplorerBundle.message("runAnything.group")

    override fun getCompletionGroupTitle(): String = MoonExplorerBundle.message("runAnything.group")

    override fun getHelpCommandPlaceholder(): String = MoonExplorerBundle.message("runAnything.help.placeholder")

    override fun getHelpDescription(): String = MoonExplorerBundle.message("runAnything.help.description")

    override fun getHelpIcon(): Icon = AllIcons.Nodes.Function

    override fun getIcon(value: String): Icon = AllIcons.Nodes.Function

    /** Gives [pattern] only for the command `moon run <target>`. Thus **Run Anything** runs the other moon commands in the usual way. */
    override fun findMatchingValue(dataContext: DataContext, pattern: String): String? {
        val parameters = ParametersListUtil.parse(pattern)
        if (parameters.firstOrNull() != HELP_COMMAND) return null
        return pattern.takeIf { targetOf(parameters.drop(1)) != null }
    }

    /** Gives all values for the current parameter. The **Run Anything** popup removes the values that do not match the text that the user types. */
    override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> =
        when (commandLine.completedParameters) {
            emptyList<String>() -> sequenceOf(RUN_COMMAND)
            listOf(RUN_COMMAND) -> loadedTargets(dataContext)
            else -> emptySequence()
        }

    override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val target = targetOf(commandLine.parameters) ?: return false
        runTask(project, target)
        return true
    }

    private fun loadedTargets(dataContext: DataContext): Sequence<String> {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return emptySequence()
        val tasks = project.service<MoonTasksService>().state.value.loadedTasks ?: return emptySequence()
        return tasks.allTasks.asSequence().map { it.target }
    }

    internal companion object {
        const val HELP_COMMAND = "moon"
        const val RUN_COMMAND = "run"

        /**
         * Gives the target if [parameters] are `run <target>`. The parameters do not contain `moon`.
         * The function accepts all targets. It also accepts targets that the plugin did not load, for example `:build`.
         * For other parameters, the function gives null.
         * An option is not a target, for example `--help`. Thus **Run Anything** runs `moon run --help` in the usual way.
         */
        fun targetOf(parameters: List<String>): String? =
            parameters.takeIf { it.size == 2 && it[0] == RUN_COMMAND && it[1].isNotEmpty() && !it[1].startsWith("-") }?.get(1)
    }
}
