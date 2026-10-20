package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.testData.TestData

/** Shows the recorded tasks of the test workspace in the panel. Call this function on the EDT. */
fun MoonTasksPanel.loadRecorded() = render(TasksState.Loaded(TestData.recordedTasks(), TestData.workspaceRoot))

/** Makes an event for [action] from the toolbar of [panel]. The data context contains [project] and [panel]. */
fun tasksPanelEvent(action: AnAction, project: Project, panel: MoonTasksPanel): AnActionEvent = TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
)
