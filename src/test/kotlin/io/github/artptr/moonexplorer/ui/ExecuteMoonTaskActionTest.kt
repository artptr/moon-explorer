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
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.loadStale
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.model.startEndlessRefresh
import kotlinx.coroutines.runBlocking

class ExecuteMoonTaskActionTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            project.service<RefreshFailureNotifier>().onState(TasksState.Loading(null))
            project.service<RefreshFailureNotifier>().onState(TasksState.NoWorkspace)
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun isEnabled(): Boolean {
        val action = ExecuteMoonTaskAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        return event.presentation.isEnabled
    }

    fun testActionIsEnabledAfterASuccessfulLoad() {
        service.loadRecorded()
        assertTrue(isEnabled())
    }

    fun testActionIsEnabledWhenTheTreeIsStale() {
        service.loadStale("bad config")
        assertTrue(isEnabled())
    }

    fun testActionIsEnabledDuringARefreshAfterASuccessfulLoad() {
        service.loadRecorded()
        service.startEndlessRefresh()
        assertTrue(isEnabled())
    }

    fun testActionIsDisabledBeforeTheFirstLoad() {
        assertEquals(TasksState.Loading(null), service.state.value)
        assertFalse(isEnabled())
    }

    fun testActionIsDisabledWithoutAWorkspace() {
        service.loadNoWorkspace()
        assertFalse(isEnabled())
    }

    fun testActionIsDisabledWhenTheTreeShowsAnError() {
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("bad config")))
        assertFalse(isEnabled())
    }

    fun testActionOpensRunAnythingWithTheMoonCommand() {
        service.loadRecorded()
        val texts = mutableListOf<String>()
        val action = ExecuteMoonTaskAction()
        action.showRunAnything = { _, text, _ -> texts += text }
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        assertEquals(listOf("moon "), texts)
    }
}
