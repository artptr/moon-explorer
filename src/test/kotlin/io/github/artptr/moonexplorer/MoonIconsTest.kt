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

    override fun setUp() {
        super.setUp()
        // In a headless environment, for example CI, the IDE does not load icons. Then each icon has the size 1 × 1.
        IconLoader.activate()
    }

    override fun tearDown() {
        try {
            IconLoader.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testIconFilesExist() {
        val files = listOf("moon", "expui/moon", "toolWindowMoon", "toolWindowMoon@20x20").flatMap { listOf("$it.svg", "${it}_dark.svg") }
        for (file in files) {
            assertNotNull(file, MoonIcons::class.java.getResource("/icons/$file"))
        }
    }

    fun testIconsHaveTheSizeOfANodeIcon() {
        for (icon in listOf(MoonIcons.Moon, MoonIcons.ToolWindow)) {
            assertEquals(16, icon.iconWidth)
            assertEquals(16, icon.iconHeight)
        }
    }

    fun testToolWindowShowsTheOutlineIcon() {
        val toolWindow = ToolWindowEP.EP_NAME.extensionList.single { it.id == "Moon" }
        assertEquals("io.github.artptr.moonexplorer.MoonIcons.ToolWindow", toolWindow.icon)
    }

    fun testTaskNodesShowTheMoonIcon() {
        assertSame(MoonIcons.Moon, TaskNode(task).icon)
        assertSame(MoonIcons.Moon, TaskNameNode("build").icon)
    }

    fun testRunConfigurationTypeShowsTheMoonIcon() {
        assertSame(MoonIcons.Moon, MoonRunConfigurationType.getInstance().icon)
    }
}
