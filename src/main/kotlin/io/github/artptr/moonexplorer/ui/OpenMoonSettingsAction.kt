package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.settings.MoonSettingsConfigurable

/**
 * Opens the **Settings** dialog on the page **Tools | Moon Explorer**.
 * The action is available in all conditions of the tree. It is also available when the tree shows an error.
 */
class OpenMoonSettingsAction : DumbAwareAction() {

    /** Opens the settings page. Tests replace this value with a test function, because the real function opens a modal dialog. */
    internal var showSettings: (Project) -> Unit = MoonSettingsConfigurable::show

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showSettings(project)
    }
}
