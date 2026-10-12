package io.github.artptr.moonexplorer.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import io.github.artptr.moonexplorer.MoonExplorerBundle
import javax.swing.SwingConstants

/**
 * Creates the "Moon" tool window. The content is a placeholder until the first feature lands.
 */
class MoonToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val label = JBLabel(MoonExplorerBundle.message("toolWindow.placeholder"), SwingConstants.CENTER).apply {
            border = JBUI.Borders.empty(8)
        }
        val content = ContentFactory.getInstance().createContent(label, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
