package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.run.MoonTaskRunner

/** The task that the user selected in the tree of the Moon tool window. The value is null if the selection is not one task. */
val SELECTED_TASK: DataKey<MoonTask> = DataKey.create("MoonExplorer.SelectedTask")

/** The base of the actions for the selected task. The action shows only if one task is selected. */
abstract class SelectedTaskAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val task = e.getData(SELECTED_TASK)
        e.presentation.isEnabledAndVisible = e.project != null && task != null
        if (task != null) update(e, task)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val task = e.getData(SELECTED_TASK) ?: return
        perform(project, task)
    }

    /** Changes the presentation for [task]. */
    protected open fun update(e: AnActionEvent, task: MoonTask) = Unit

    protected abstract fun perform(project: Project, task: MoonTask)
}

/** Runs the selected task with a temporary run configuration. */
class RunTaskAction : SelectedTaskAction() {

    override fun update(e: AnActionEvent, task: MoonTask) {
        e.presentation.text = MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", task.target)
    }

    override fun perform(project: Project, task: MoonTask) {
        MoonTaskRunner.run(project, task.target)
    }
}

/** Saves a run configuration for the selected task. */
class CreateRunConfigurationAction : SelectedTaskAction() {

    override fun perform(project: Project, task: MoonTask) {
        MoonTaskRunner.createConfiguration(project, task.target)
    }
}
