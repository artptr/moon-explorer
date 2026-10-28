package io.github.artptr.moonexplorer.ui

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.TaskKeyTargets

/**
 * Gives the run icon in the gutter for a task key with [targets].
 *
 * - [TaskKeyTargets.Single]: the usual run icon. The popup contains the actions of the executors. These actions use the run configuration
 *   producer for the configuration file. Thus they use a saved run configuration for the target if there is one.
 * - [TaskKeyTargets.Shared]: the icon for more than one run. The tooltip shows the number of projects. The popup contains
 *   **Run in N Projects**, **Run Affected in N Projects**, and one item for each project. Each command contains the full list of targets.
 *
 * The function does not use YAML classes. Thus a contributor for a different file format can also use it.
 */
fun taskGutterInfo(targets: TaskKeyTargets): RunLineMarkerContributor.Info = when (targets) {
    is TaskKeyTargets.Single -> {
        val tooltip = MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", targets.target)
        RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0)) { tooltip }
    }
    is TaskKeyTargets.Shared -> {
        val all = targets.targets
        val tooltip = MoonExplorerBundle.message("gutter.sharedTask.tooltip", all.size)
        val actions = listOf<AnAction>(
            RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunInProjects.textWithCount", all.size), all, affected = false),
            RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunAffectedInProjects.textWithCount", all.size), all, affected = true),
            Separator.getInstance(),
        ) + all.map { target ->
            RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", target), listOf(target), affected = false)
        }
        RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run_run, actions.toTypedArray()) { tooltip }
    }
}
