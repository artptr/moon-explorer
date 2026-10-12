package io.github.artptr.moonexplorer

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.toolWindow.MoonToolWindowFactory

class MoonToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegistered() {
        val registered = ToolWindowEP.EP_NAME.extensionList.any { it.id == "Moon" }
        assertTrue("The Moon tool window must be registered in plugin.xml", registered)
    }

    fun testBundleHasPlaceholderText() {
        assertFalse(MoonExplorerBundle.message("toolWindow.placeholder").isBlank())
    }

    fun testFactoryAddsContent() {
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
        MoonToolWindowFactory().createToolWindowContent(project, toolWindow)
        assertEquals(1, toolWindow.contentManager.contentCount)
    }
}
