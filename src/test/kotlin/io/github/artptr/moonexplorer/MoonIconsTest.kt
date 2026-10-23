package io.github.artptr.moonexplorer

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.run.MoonRunConfigurationType
import io.github.artptr.moonexplorer.ui.TaskNameNode
import io.github.artptr.moonexplorer.ui.TaskNode

class MoonIconsTest : BasePlatformTestCase() {

    private val task = MoonTask("build", "web:build", null)

    fun testIconFilesExist() {
        for (newUi in listOf(true, false)) {
            val path = MoonIcons.moonIconPath(newUi)
            assertNotNull(path, IconLoader.findIcon(path, MoonIcons::class.java))
            assertNotNull("The dark theme needs a _dark file for $path", MoonIcons::class.java.getResource(path.replace(".svg", "_dark.svg")))
        }
    }

    fun testNewUiAndClassicUiHaveDifferentIcons() {
        assertEquals("/icons/expui/moon.svg", MoonIcons.moonIconPath(true))
        assertEquals("/icons/moon.svg", MoonIcons.moonIconPath(false))
    }

    fun testToolWindowIconFilesExist() {
        for (name in listOf("toolWindowMoon.svg", "toolWindowMoon_dark.svg", "toolWindowMoon@20x20.svg", "toolWindowMoon@20x20_dark.svg")) {
            assertNotNull(name, MoonIcons::class.java.getResource("/icons/$name"))
        }
        assertEquals(16, MoonIcons.ToolWindow.iconWidth)
    }

    fun testToolWindowShowsTheOutlineIcon() {
        val toolWindow = ToolWindowEP.EP_NAME.extensionList.single { it.id == "Moon" }
        assertEquals("io.github.artptr.moonexplorer.MoonIcons.ToolWindow", toolWindow.icon)
    }

    fun testIconHasTheSizeOfANodeIcon() {
        assertEquals(16, MoonIcons.Moon.iconWidth)
        assertEquals(16, MoonIcons.Moon.iconHeight)
    }

    fun testTaskNodesShowTheMoonIcon() {
        assertSame(MoonIcons.Moon, TaskNode(task).icon)
        assertSame(MoonIcons.Moon, TaskNameNode("build").icon)
    }

    fun testRunConfigurationTypeShowsTheMoonIcon() {
        assertSame(MoonIcons.Moon, MoonRunConfigurationType.getInstance().icon)
    }
}
