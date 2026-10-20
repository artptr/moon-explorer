package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * In the tree of the Moon tool window, selects the project of the file in the editor.
 * The action is not available if the main group is not Project, or if the editor does not show a local file.
 */
class SelectOpenedFileAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val panel = e.getData(TASKS_PANEL)
        e.presentation.isVisible = panel != null
        e.presentation.isEnabled = panel != null && panel.canSelectProject() && e.project?.let(::openedFile) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val panel = e.getData(TASKS_PANEL) ?: return
        val file = e.project?.let(::openedFile) ?: return
        panel.selectProjectOf(file.toNioPath())
    }

    /**
     * Gives the file of the selected editor. If the IDE shows split editors, this editor is in the current editor window.
     * The value is null if there is no editor, or if the file is not on the local disk.
     */
    private fun openedFile(project: Project): VirtualFile? =
        FileEditorManager.getInstance(project).selectedEditor?.file?.takeIf { it.isInLocalFileSystem }
}
