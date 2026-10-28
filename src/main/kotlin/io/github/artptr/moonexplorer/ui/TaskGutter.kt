package io.github.artptr.moonexplorer.ui

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
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
    is TaskKeyTargets.Single -> RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0)) {
        MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", targets.target)
    }
    is TaskKeyTargets.Shared -> {
        val all = targets.targets
        val actions = arrayOf<AnAction>(
            RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunInProjects.textWithCount", all.size), all, affected = false),
            RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunAffectedInProjects.textWithCount", all.size), all, affected = true),
            Separator.getInstance(),
            RunInEachProjectGroup(all),
        )
        RunLineMarkerContributor.Info(AllIcons.RunConfigurations.TestState.Run_run, actions) {
            MoonExplorerBundle.message("gutter.sharedTask.tooltip", all.size)
        }
    }
}

/**
 * The popup items that run the task in one project each. The popup shows the items of the group without a submenu.
 * The group makes the items only when the popup opens. Thus the analysis of a file does not make one action for each project and each key.
 */
internal class RunInEachProjectGroup(private val targets: List<String>) : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = targets.map { target ->
        RunTargetsAction(MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", target), listOf(target), affected = false)
    }.toTypedArray()
}
