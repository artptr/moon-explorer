package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState

class RefreshTasksActionTest : MoonTasksServiceTestCase() {

    fun testActionIsEnabledOnlyWithProject() {
        assertTrue(RefreshTasksAction().updatedPresentation(SimpleDataContext.getProjectContext(project)).isEnabled)
        assertFalse(RefreshTasksAction().updatedPresentation(SimpleDataContext.EMPTY_CONTEXT).isEnabled)
    }

    fun testActionRefreshesTheTasks() {
        service.queryProjects = { CliResult.Failed("from action") }
        val action = RefreshTasksAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("from action")))
    }
}
