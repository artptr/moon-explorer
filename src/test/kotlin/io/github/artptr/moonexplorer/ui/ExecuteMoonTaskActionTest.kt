package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.model.*

/** [TasksStateTest][io.github.artptr.moonexplorer.model.TasksStateTest] examines `lastLoaded` for all states. Thus this test only examines some states. */
class ExecuteMoonTaskActionTest : MoonTasksServiceTestCase() {

    private fun isEnabled(context: DataContext = SimpleDataContext.getProjectContext(project)): Boolean =
        ExecuteMoonTaskAction().updatedPresentation(context).isEnabled

    fun testActionIsEnabledWhenTheTreeIsStale() {
        service.loadStale("bad config")
        assertTrue(isEnabled())
    }

    fun testActionIsEnabledForAWorkspaceWithoutTasks() {
        service.loadJson("""{"projects": [], "options": {}}""")
        assertTrue(isEnabled())
    }

    fun testActionIsDisabledWithoutProject() {
        service.loadRecorded()
        assertFalse(isEnabled(SimpleDataContext.EMPTY_CONTEXT))
    }

    fun testActionIsDisabledBeforeTheFirstLoadAndWhenTheTreeShowsAnError() {
        assertFalse(isEnabled())
        service.loadFailed("bad config")
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
