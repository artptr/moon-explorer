package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.loadStale
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.model.startEndlessRefresh
import io.github.artptr.moonexplorer.testData.TestData
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

    private fun isEnabled(context: DataContext = SimpleDataContext.getProjectContext(project)): Boolean {
        val action = ExecuteMoonTaskAction()
        val event = TestActionEvent.createTestEvent(action, context)
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

    fun testActionIsEnabledForAWorkspaceWithoutTasks() {
        service.queryProjects = { CliResult.Success("""{"projects": [], "options": {}}""") }
        service.refresh()
        service.awaitState(TestData.loadedState(WorkspaceTasks(emptyList())))
        assertTrue(isEnabled())
    }

    fun testActionIsDisabledWithoutProject() {
        service.loadRecorded()
        assertFalse(isEnabled(SimpleDataContext.EMPTY_CONTEXT))
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

    fun testActionTextComesFromTheBundle() {
        val presentation = ActionManager.getInstance().getAction("MoonExplorer.ExecuteMoonTask").templatePresentation
        assertEquals(MoonExplorerBundle.message("action.MoonExplorer.ExecuteMoonTask.text"), presentation.text)
        assertEquals(MoonExplorerBundle.message("action.MoonExplorer.ExecuteMoonTask.description"), presentation.description)
    }
}
