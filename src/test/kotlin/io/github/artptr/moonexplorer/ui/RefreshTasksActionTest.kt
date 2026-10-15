package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path

class RefreshTasksActionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light project is the same for all tests. Remove the state and the load of the previous test.
        runBlocking { project.service<MoonTasksService>().resetForTests() }
    }

    override fun tearDown() {
        try {
            val service = project.service<MoonTasksService>()
            runBlocking { service.resetForTests() }
            service.projectDir = { project.basePath?.let(Path::of) }
            service.queryTasks = MoonCli::queryTasks
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
        val service = project.service<MoonTasksService>()
        service.projectDir = { TestData.workspace.toPath() }
        service.queryTasks = { CliResult.Failed("from action") }
        val action = RefreshTasksAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        val expected = TasksState.MoonError("from action")
        runBlocking { withTimeoutOrNull(10_000) { service.state.first { it == expected } } }
            ?: fail("Expected $expected, but the state is ${service.state.value}")
    }
}
