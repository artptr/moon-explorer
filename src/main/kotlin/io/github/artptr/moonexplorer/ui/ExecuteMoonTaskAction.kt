package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.runAnything.RunAnythingManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.lastLoaded

/**
 * Opens **Run Anything** with the text `moon `. Then **Run Anything** shows the completion of [MoonRunAnythingProvider].
 * The Gradle plugin uses the same method for its action **Execute Gradle Task**.
 * The action is available only if the tree shows tasks. If the IDE project has no moon workspace, or if the tree shows an error,
 * the action is not available.
 */
class ExecuteMoonTaskAction : DumbAwareAction() {

    /** Opens **Run Anything** with the text. Tests replace this value with a test function, because the real function opens a popup. */
    internal var showRunAnything: (Project, String, AnActionEvent) -> Unit = { project, text, e ->
        RunAnythingManager.getInstance(project).show(text, false, e)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.service<MoonTasksService>()?.state?.value?.lastLoaded != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showRunAnything(project, "${MoonRunAnythingProvider.HELP_COMMAND} ", e)
    }
}
