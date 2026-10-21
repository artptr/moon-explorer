package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class ShowRefreshFailureActionTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private val loaded get() = TestData.recordedState()
    private val stale get() = TasksState.Stale(loaded, TasksState.MoonFailed(CliResult.Failed("bad config")))

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

    private fun loadStale() {
        service.refresh()
        service.awaitState(loaded)
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(stale)
    }

    fun testActionIsHiddenAfterASuccessfulLoad() {
        service.refresh()
        service.awaitState(loaded)
        assertFalse(isVisible())
    }

    fun testActionIsHiddenForAFailureWithoutAnEarlierLoad() {
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(CliResult.Failed("bad config")))
        assertFalse(isVisible())
    }

    fun testActionIsVisibleWhenTheTreeIsStale() {
        loadStale()
        assertTrue(isVisible())
    }

    fun testActionShowsTheNotificationAgain() {
        loadStale()
        val shown = collectRefreshNotifications(project, testRootDisposable)
        val action = ShowRefreshFailureAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        assertTrue(shown.single().content.contains("bad config"))
    }
}
