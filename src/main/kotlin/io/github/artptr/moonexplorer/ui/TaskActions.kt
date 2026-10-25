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

/**
 * The tasks that the user selected in the tree of the Moon tool window.
 * The value is null if the selection has less than two nodes, or if a selected node is not a task node.
 */
val SELECTED_TASKS: DataKey<List<MoonTask>> = DataKey.create("MoonExplorer.SelectedTasks")

/**
 * The tasks in the task name node that the user selected in the tree of the Moon tool window.
 * Below a category node, the task name node contains only the projects of the category. Thus the value contains only these tasks.
 * The value is null if the selection is not one task name node.
 */
val SELECTED_TASK_NAME_TASKS: DataKey<List<MoonTask>> = DataKey.create("MoonExplorer.SelectedTaskNameTasks")

/** The base of the actions for the selected task. The action is visible only if the user selects one task. */
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

/**
 * The base of the actions that run the tasks in [key] with one moon command. The action is visible only if [key] has a value.
 * The command contains the full list of targets. If [affected] is true, moon runs only the affected tasks.
 */
abstract class SelectedTasksRunAction(private val key: DataKey<List<MoonTask>>, private val affected: Boolean) : DumbAwareAction() {

    /** Runs the targets. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var run: (Project, List<String>, Boolean) -> Unit = MoonTaskRunner::run

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val tasks = e.getData(key)
        e.presentation.isEnabledAndVisible = e.project != null && tasks != null
        if (tasks != null) e.presentation.text = text(tasks.size)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tasks = e.getData(key) ?: return
        run(project, tasks.map { it.target }, affected)
    }

    /** Gives the text of the action for [count] tasks. */
    protected abstract fun text(count: Int): String
}

/** Runs the task of the selected task name node in all projects of the node. */
class RunInProjectsAction : SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = false) {
    override fun text(count: Int): String = MoonExplorerBundle.message("action.MoonExplorer.RunInProjects.textWithCount", count)
}

/** Runs the task of the selected task name node only in the projects of the node that the changed files affect. */
class RunAffectedInProjectsAction : SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = true) {
    override fun text(count: Int): String = MoonExplorerBundle.message("action.MoonExplorer.RunAffectedInProjects.textWithCount", count)
}

/** Runs the selected tasks with one moon command. */
class RunSelectedTasksAction : SelectedTasksRunAction(SELECTED_TASKS, affected = false) {
    override fun text(count: Int): String = MoonExplorerBundle.message("action.MoonExplorer.RunSelectedTasks.textWithCount", count)
}
