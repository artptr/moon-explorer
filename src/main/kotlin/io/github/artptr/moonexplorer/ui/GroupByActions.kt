package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping

/** The content of the Moon tool window. The actions of the toolbar use it. */
val TASKS_PANEL: DataKey<MoonTasksPanel> = DataKey.create("MoonExplorer.TasksPanel")

/** A selection in the **Group By** menu of the Moon tool window. The plugin does not show the action if there is no tasks panel. */
abstract class TasksPanelToggleAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getData(TASKS_PANEL) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean = e.getData(TASKS_PANEL)?.let(::isSelected) == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) e.getData(TASKS_PANEL)?.let(::select)
    }

    /** Gives true if [panel] uses the value of this action. */
    protected abstract fun isSelected(panel: MoonTasksPanel): Boolean

    /** Sets the value of this action in [panel]. */
    protected abstract fun select(panel: MoonTasksPanel)
}

/** Selects the main group of the tree. */
abstract class GroupByAction(private val grouping: TaskGrouping) : TasksPanelToggleAction() {
    override fun isSelected(panel: MoonTasksPanel): Boolean = panel.grouping == grouping
    override fun select(panel: MoonTasksPanel) = panel.changeGrouping(grouping)
}

class GroupByProjectAction : GroupByAction(TaskGrouping.PROJECT)

class GroupByTaskNameAction : GroupByAction(TaskGrouping.TASK_NAME)

/** Selects the category nodes at the top of the tree. */
abstract class CategoryAction(private val category: TaskCategory) : TasksPanelToggleAction() {
    override fun isSelected(panel: MoonTasksPanel): Boolean = panel.category == category
    override fun select(panel: MoonTasksPanel) = panel.changeCategory(category)
}

class CategoryNoneAction : CategoryAction(TaskCategory.NONE)

class CategoryStackAction : CategoryAction(TaskCategory.STACK)

class CategoryLayerAction : CategoryAction(TaskCategory.LAYER)
