package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.loadStale
import io.github.artptr.moonexplorer.model.prepareForTests
import kotlinx.coroutines.runBlocking

class ShowRefreshFailureActionTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            project.service<RefreshFailureNotifier>().onState(TasksState.Loading)
            project.service<RefreshFailureNotifier>().onState(TasksState.NoWorkspace)
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun isVisible(): Boolean {
        val action = ShowRefreshFailureAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        return event.presentation.isVisible
    }

    fun testActionIsHiddenAfterASuccessfulLoad() {
        service.loadRecorded()
        assertFalse(isVisible())
    }

    fun testActionIsHiddenForAFailureWithoutAnEarlierLoad() {
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("bad config")))
        assertFalse(isVisible())
    }

    fun testActionIsVisibleWhenTheTreeIsStale() {
        service.loadStale("bad config")
        assertTrue(isVisible())
    }

    fun testActionShowsTheNotificationAgain() {
        service.loadStale("bad config")
        val shown = collectRefreshNotifications(project, testRootDisposable)
        val action = ShowRefreshFailureAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        assertTrue(shown.single().content.contains("bad config"))
    }
}
