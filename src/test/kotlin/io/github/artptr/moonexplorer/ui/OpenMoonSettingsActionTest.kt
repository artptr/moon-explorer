package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.loadFailed

class OpenMoonSettingsActionTest : MoonTasksServiceTestCase() {

    private fun isEnabled(context: DataContext = SimpleDataContext.getProjectContext(project)): Boolean =
        OpenMoonSettingsAction().updatedPresentation(context).isEnabled

    fun testActionIsEnabledOnlyWithProject() {
        assertTrue(isEnabled())
        assertFalse(isEnabled(SimpleDataContext.EMPTY_CONTEXT))
    }

    fun testActionIsEnabledWhenTheTreeShowsAnError() {
        service.loadFailed("moon failed")
        assertTrue(isEnabled())
    }

    fun testActionShowsTheSettingsOfTheProject() {
        val projects = mutableListOf<Project>()
        val action = OpenMoonSettingsAction()
        action.showSettings = { projects += it }
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project)))
        assertEquals(listOf(project), projects)
    }
}
