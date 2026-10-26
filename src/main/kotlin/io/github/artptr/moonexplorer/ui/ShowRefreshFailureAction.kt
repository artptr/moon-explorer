package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.refreshFailure

/**
 * Shows the error of the last refresh again. The toolbar shows this action only if the tree shows old tasks ([TasksState.Stale]),
 * also during the next load. Refer to [refreshFailure]. Thus the user sees the error also after the notification closes.
 */
class ShowRefreshFailureAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.service<MoonTasksService>()?.state?.value?.refreshFailure != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<RefreshFailureNotifier>()?.showAgain()
    }
}
