package io.github.artptr.moonexplorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import io.github.artptr.moonexplorer.ui.MoonToolWindowFactory

class MoonToolWindowTest : BasePlatformTestCase() {

    fun testFactoryAddsContent() {
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
        MoonToolWindowFactory().createToolWindowContent(project, toolWindow)
        assertEquals(1, toolWindow.contentManager.contentCount)
    }

    fun testToolbarLayout() {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("MoonExplorer.TasksToolbar") as DefaultActionGroup
        val ids = group.childActionsOrStubs.map { actionManager.getId(it) }
        assertEquals(
            listOf(
                "MoonExplorer.RefreshTasks",
                "MoonExplorer.ExecuteMoonTask",
                "MoonExplorer.ShowRefreshFailure",
                "MoonExplorer.SelectOpenedFile",
                "MoonExplorer.GroupBy",
                // The separator has no ID.
                null,
                "MoonExplorer.OpenSettings",
            ),
            ids,
        )
    }

    fun testTaskActionsAreInTheContextMenu() {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("MoonExplorer.TaskPopup") as DefaultActionGroup
        val ids = group.childActionsOrStubs.map { actionManager.getId(it) }
        assertEquals(
            listOf(
                "MoonExplorer.RunTask",
                "MoonExplorer.RunInProjects",
                "MoonExplorer.RunAffectedInProjects",
                "MoonExplorer.RunSelectedTasks",
                "MoonExplorer.EditTaskSettings",
                // The separator has no ID.
                null,
                "MoonExplorer.JumpToSource",
            ),
            ids,
        )
    }

    fun testActionsOfTheToolbarAndTheContextMenuHaveText() {
        val actionManager = ActionManager.getInstance()
        for (groupId in listOf("MoonExplorer.TasksToolbar", "MoonExplorer.GroupBy", "MoonExplorer.TaskPopup")) {
            val group = actionManager.getAction(groupId) as DefaultActionGroup
            for (action in group.childActionsOrStubs.filter { it !is Separator }) {
                assertFalse("The action ${actionManager.getId(action)} has no text", action.templatePresentation.text.isNullOrBlank())
            }
        }
    }
}
