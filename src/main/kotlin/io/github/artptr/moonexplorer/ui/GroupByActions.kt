package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.github.artptr.moonexplorer.model.TaskGrouping

/** The content of the Moon tool window. The actions of the toolbar use it. */
val TASKS_PANEL: DataKey<MoonTasksPanel> = DataKey.create("MoonExplorer.TasksPanel")

/** Selects how the tree of the Moon tool window puts the tasks into groups. */
abstract class GroupByAction(private val grouping: TaskGrouping) : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getData(TASKS_PANEL) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean = e.getData(TASKS_PANEL)?.grouping == grouping

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) e.getData(TASKS_PANEL)?.changeGrouping(grouping)
    }
}

class GroupByProjectAction : GroupByAction(TaskGrouping.PROJECT)

class GroupByTaskNameAction : GroupByAction(TaskGrouping.TASK_NAME)

class GroupByStackAction : GroupByAction(TaskGrouping.STACK)
