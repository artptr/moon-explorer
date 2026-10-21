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
import kotlinx.coroutines.runBlocking

class RefreshTasksActionTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    fun testActionIsEnabledWithProject() {
        val action = RefreshTasksAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        assertTrue(event.presentation.isEnabled)
    }

    fun testActionIsDisabledWithoutProject() {
        val action = RefreshTasksAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.EMPTY_CONTEXT)
        action.update(event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionRefreshesTheTasks() {
        service.queryProjects = { CliResult.Failed("from action") }
        val action = RefreshTasksAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        service.awaitState(TasksState.MoonFailed(CliResult.Failed("from action")))
    }
}
