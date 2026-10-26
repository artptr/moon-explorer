package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.TasksState
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

/** The project that the user selected in the tree of the Moon tool window. The value is null if the selection is not one project node. */
val SELECTED_PROJECT: DataKey<MoonProject> = DataKey.create("MoonExplorer.SelectedProject")

/**
 * The loaded state that the tree of the Moon tool window shows. The value is null if the tool window shows a message.
 * During a load, the tree shows the old tasks. Thus the value is the state of the old tasks.
 */
val SHOWN_TASKS: DataKey<TasksState.Loaded> = DataKey.create("MoonExplorer.ShownTasks")

/** The base of the actions for the selection in the tree. The action is visible only if [key] has a value. */
abstract class SelectedDataAction<T : Any>(private val key: DataKey<T>) : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val data = e.getData(key)?.takeIf(::isApplicable)
        e.presentation.isEnabledAndVisible = e.project != null && data != null
        if (data != null) update(e, data)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val data = e.getData(key)?.takeIf(::isApplicable) ?: return
        perform(project, data)
    }

    /** Gives true if the action operates on [data]. If not, the action is not visible. */
    protected open fun isApplicable(data: T): Boolean = true

    /** Changes the presentation for [data]. */
    protected open fun update(e: AnActionEvent, data: T) = Unit

    protected abstract fun perform(project: Project, data: T)
}

/** The base of the actions for the selected task. The action is visible only if the user selects one task. */
abstract class SelectedTaskAction : SelectedDataAction<MoonTask>(SELECTED_TASK)

/** Runs the selected task with a temporary run configuration. */
class RunTaskAction : SelectedTaskAction() {

    override fun update(e: AnActionEvent, data: MoonTask) {
        e.presentation.text = MoonExplorerBundle.message("action.MoonExplorer.RunTask.textWithTarget", data.target)
    }

    override fun perform(project: Project, data: MoonTask) {
        MoonTaskRunner.run(project, data.target)
    }
}

/** Saves a run configuration for the selected task. */
class CreateRunConfigurationAction : SelectedTaskAction() {

    override fun perform(project: Project, data: MoonTask) {
        MoonTaskRunner.createConfiguration(project, data.target)
    }
}

/**
 * The base of the actions that run the tasks in [key] with one moon command. The action is visible only if [key] has a value.
 * The command contains the full list of targets. If [affected] is true, moon runs only the affected tasks.
 * The message [textKey] gives the text of the action. Its parameter is the number of tasks.
 */
abstract class SelectedTasksRunAction(
    key: DataKey<List<MoonTask>>,
    private val affected: Boolean,
    private val textKey: String,
) : SelectedDataAction<List<MoonTask>>(key) {

    /** Runs the targets. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var run: (Project, List<String>, Boolean) -> Unit = MoonTaskRunner::run

    /** An empty list has no targets. Then moon has nothing to run. */
    override fun isApplicable(data: List<MoonTask>): Boolean = data.isNotEmpty()

    override fun update(e: AnActionEvent, data: List<MoonTask>) {
        e.presentation.text = MoonExplorerBundle.message(textKey, data.size)
    }

    override fun perform(project: Project, data: List<MoonTask>) {
        run(project, data.map { it.target }, affected)
    }
}

/** Runs the task of the selected task name node in all projects of the node. */
class RunInProjectsAction :
    SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = false, "action.MoonExplorer.RunInProjects.textWithCount")

/** Runs the task of the selected task name node only in the projects of the node that the changed files affect. */
class RunAffectedInProjectsAction :
    SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = true, "action.MoonExplorer.RunAffectedInProjects.textWithCount")

/** Runs the selected tasks with one moon command. */
class RunSelectedTasksAction :
    SelectedTasksRunAction(SELECTED_TASKS, affected = false, "action.MoonExplorer.RunSelectedTasks.textWithCount")
