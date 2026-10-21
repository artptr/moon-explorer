package io.github.artptr.moonexplorer.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.settings.MoonSettingsConfigurable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shows a notification when a refresh fails after a successful load ([TasksState.Stale]). Then the tree shows old tasks.
 * The notifier shows a maximum of one notification. A new failure replaces the notification. A successful load closes it.
 * The notifier also operates if the IDE did not make the Moon tool window.
 */
@Service(Service.Level.PROJECT)
class RefreshFailureNotifier(private val project: Project, private val scope: CoroutineScope) {

    /** The notification that the IDE shows now. Use it only on the EDT. */
    internal var notification: Notification? = null
        private set

    init {
        // The IDE makes a service one time. Thus only one coroutine monitors the state.
        val service = project.service<MoonTasksService>()
        scope.launch(Dispatchers.EDT) {
            service.state.collect(::onState)
        }
    }

    /** Shows or closes the notification for [state]. Call this function on the EDT. */
    internal fun onState(state: TasksState) {
        when (state) {
            is TasksState.Stale -> show(state.failure)
            TasksState.Loading -> Unit
            else -> expire()
        }
    }

    /** Shows the notification of the current [TasksState.Stale] state again. Call this function on the EDT. */
    fun showAgain() {
        val state = project.service<MoonTasksService>().state.value as? TasksState.Stale ?: return
        show(state.failure)
    }

    private fun show(failure: TasksState.Failure) {
        expire()
        val content = MoonExplorerBundle.message("notification.refreshFailed.content") + "<br><br>" + plainTextHtml(failureText(failure))
        val created = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
            .createNotification(MoonExplorerBundle.message("notification.refreshFailed.title"), content, NotificationType.ERROR)
        created.addAction(
            NotificationAction.createSimpleExpiring(MoonExplorerBundle.message("notification.action.refresh")) {
                project.service<MoonTasksService>().refresh()
            },
        )
        if (hasSettingsLink(failure)) {
            created.addAction(
                NotificationAction.createSimple(MoonExplorerBundle.message("notification.action.openSettings")) {
                    MoonSettingsConfigurable.show(project)
                },
            )
        }
        notification = created
        created.notify(project)
    }

    private fun expire() {
        notification?.expire()
        notification = null
    }

    internal companion object {
        /** The ID of the notification group in `plugin.xml`. */
        const val GROUP_ID = "Moon Explorer"
    }
}

/**
 * Starts [RefreshFailureNotifier] when the user opens the IDE project. The service starts to monitor the state when the IDE makes it.
 * The model layer does not use the UI layer. Thus `MoonTasksStartupActivity` does not start the notifier.
 */
class RefreshFailureNotifierStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<RefreshFailureNotifier>()
    }
}
