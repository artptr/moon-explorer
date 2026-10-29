package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import kotlinx.coroutines.runBlocking

class OpenMoonSettingsActionTest : BasePlatformTestCase() {

    private fun isEnabled(context: DataContext = SimpleDataContext.getProjectContext(project)): Boolean {
        val action = OpenMoonSettingsAction()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        return event.presentation.isEnabled
    }

    fun testActionIsEnabledWithProject() {
        assertTrue(isEnabled())
    }

    fun testActionIsDisabledWithoutProject() {
        assertFalse(isEnabled(SimpleDataContext.EMPTY_CONTEXT))
    }

    fun testActionIsEnabledWhenTheTreeShowsAnError() {
        val service = project.service<MoonTasksService>()
        service.prepareForTests()
        try {
            service.queryProjects = { CliResult.Failed("moon failed") }
            service.refresh()
            service.awaitState(TasksState.MoonFailed(MoonFailure.Error("moon failed")))
            assertTrue(isEnabled())
        } finally {
            runBlocking { service.resetForTests() }
        }
    }

    fun testActionShowsTheSettingsOfTheProject() {
        val projects = mutableListOf<Project>()
        val action = OpenMoonSettingsAction()
        action.showSettings = { projects += it }
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        assertEquals(listOf(project), projects)
    }

    fun testActionTextComesFromTheBundle() {
        val presentation = ActionManager.getInstance().getAction("MoonExplorer.OpenSettings").templatePresentation
        assertEquals(MoonExplorerBundle.message("action.MoonExplorer.OpenSettings.text"), presentation.text)
        assertEquals(MoonExplorerBundle.message("action.MoonExplorer.OpenSettings.description"), presentation.description)
    }
}
