package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.model.*

/** [TasksStateTest][io.github.artptr.moonexplorer.model.TasksStateTest] examines `refreshFailure` for all states. Thus this test only examines some states. */
class ShowRefreshFailureActionTest : MoonTasksServiceTestCase() {

    override fun tearDown() {
        try {
            project.service<RefreshFailureNotifier>().onState(TasksState.Loading(null))
            project.service<RefreshFailureNotifier>().onState(TasksState.NoWorkspace)
        } finally {
            super.tearDown()
        }
    }

    private fun isVisible(): Boolean = ShowRefreshFailureAction().updatedPresentation(SimpleDataContext.getProjectContext(project)).isVisible

    private fun perform() {
        val action = ShowRefreshFailureAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
    }

    fun testActionIsHiddenAfterASuccessfulLoad() {
        service.loadRecorded()
        assertFalse(isVisible())
    }

    fun testActionIsHiddenForAFailureWithoutAnEarlierLoad() {
        service.loadFailed("bad config")
        assertFalse(isVisible())
    }

    fun testActionIsVisibleWhenTheTreeIsStale() {
        service.loadStale("bad config")
        assertTrue(isVisible())
    }

    fun testActionShowsTheNotificationAgainAlsoDuringARefresh() {
        service.loadStale("bad config")
        val shown = collectRefreshNotifications(project, testRootDisposable)
        perform()
        service.startEndlessRefresh()
        perform()
        assertEquals(2, shown.size)
        assertTrue(shown.all { it.content.contains("bad config") })
    }
}
