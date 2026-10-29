package io.github.artptr.moonexplorer.ui

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunDialog
import com.intellij.icons.AllIcons
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

/**
 * The task that the user selected in the tree of the Moon tool window. The value is null if the selection is not one task.
 * The tree can show a task in more than one category node. If the user selects two nodes of the same task, the value is this task.
 */
val SELECTED_TASK: DataKey<MoonTask> = DataKey.create("MoonExplorer.SelectedTask")

/**
 * The tasks that the user selected in the tree of the Moon tool window.
 * The value is null if the selection has less than two tasks, or if a selected node is not a task node. The list contains each task one time.
 */
val SELECTED_TASKS: DataKey<List<MoonTask>> = DataKey.create("MoonExplorer.SelectedTasks")

/**
 * The tasks in the task name nodes that the user selected in the tree of the Moon tool window.
 * Below a category node, the task name node contains only the projects of the category. Thus the value contains only these tasks.
 * The user can select the nodes of one task name in more than one category node. Then the value contains the tasks of all these nodes, each task one time.
 * The value is null if a selected node is not a task name node, or if the selected nodes have different names.
 * The value is also null if the selected nodes have no task nodes.
 */
val SELECTED_TASK_NAME_TASKS: DataKey<List<MoonTask>> = DataKey.create("MoonExplorer.SelectedTaskNameTasks")

/**
 * The project that the user selected in the tree of the Moon tool window. The value is null if the selection is not one project.
 * The tree can show a project in more than one category node. If the user selects two nodes of the same project, the value is this project.
 */
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

/** Opens the run configuration of the selected task in the configuration dialog. If there is no configuration, the dialog shows a new one. */
class EditTaskSettingsAction : SelectedTaskAction() {

    /** Opens the dialog. Tests replace this value with a test function, because the dialog cannot open in tests. */
    internal var edit: (Project, RunnerAndConfigurationSettings, String) -> Boolean = RunDialog::editConfiguration

    override fun update(e: AnActionEvent, data: MoonTask) {
        e.presentation.text = MoonExplorerBundle.message("action.MoonExplorer.EditTaskSettings.textWithTarget", data.target)
    }

    override fun perform(project: Project, data: MoonTask) {
        MoonTaskRunner.editConfiguration(project, data.target, edit)
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

/** Runs the task of the selected task name nodes in all projects of the nodes. Refer to [SELECTED_TASK_NAME_TASKS]. */
class RunInProjectsAction :
    SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = false, "action.MoonExplorer.RunInProjects.textWithCount")

/** Runs the task of the selected task name nodes only in the projects of the nodes that the changed files affect. */
class RunAffectedInProjectsAction :
    SelectedTasksRunAction(SELECTED_TASK_NAME_TASKS, affected = true, "action.MoonExplorer.RunAffectedInProjects.textWithCount")

/** Runs the selected tasks with one moon command. */
class RunSelectedTasksAction :
    SelectedTasksRunAction(SELECTED_TASKS, affected = false, "action.MoonExplorer.RunSelectedTasks.textWithCount")

/**
 * Runs [targets] with one moon command, the same as [SelectedTasksRunAction]. If [affected] is true, moon runs only the affected tasks.
 * The gutter of a configuration file uses this action. [text] is the text of the action. The action does not find a mnemonic in [text].
 */
class RunTargetsAction(text: String, internal val targets: List<String>, internal val affected: Boolean) : DumbAwareAction() {

    /** Runs the targets. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var run: (Project, List<String>, Boolean) -> Unit = MoonTaskRunner::run

    init {
        // A task ID can contain '_'. Thus the text has no mnemonic.
        templatePresentation.setText(text, false)
        templatePresentation.icon = AllIcons.Actions.Execute
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        run(project, targets, affected)
    }
}
