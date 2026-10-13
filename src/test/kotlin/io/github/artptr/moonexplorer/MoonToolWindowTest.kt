package io.github.artptr.moonexplorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.ui.MoonToolWindowFactory
import io.github.artptr.moonexplorer.ui.RefreshTasksAction

class MoonToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegistered() {
        val registered = ToolWindowEP.EP_NAME.extensionList.any { it.id == "Moon" }
        assertTrue("The Moon tool window must be registered in plugin.xml", registered)
    }

    fun testFactoryAddsContent() {
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
        MoonToolWindowFactory().createToolWindowContent(project, toolWindow)
        assertEquals(1, toolWindow.contentManager.contentCount)
    }

    fun testRefreshActionIsInTheToolbar() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("MoonExplorer.RefreshTasks")
        assertInstanceOf(action, RefreshTasksAction::class.java)
        assertFalse(action.templatePresentation.text.isNullOrBlank())
        val group = actionManager.getAction("MoonExplorer.TasksToolbar") as DefaultActionGroup
        assertTrue(group.childActionsOrStubs.any { actionManager.getId(it) == "MoonExplorer.RefreshTasks" })
    }
}
