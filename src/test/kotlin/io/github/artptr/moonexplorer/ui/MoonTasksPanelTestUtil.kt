package io.github.artptr.moonexplorer.ui

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestActionEvent
import io.github.artptr.moonexplorer.testData.TestData

/** Shows the recorded tasks of the test workspace in the panel. Call this function on the EDT. */
fun MoonTasksPanel.loadRecorded() = render(TestData.recordedState())

/** Collects the notifications of [RefreshFailureNotifier] in [project] until [disposable] is disposed. */
fun collectRefreshNotifications(project: Project, disposable: Disposable): List<Notification> {
    val shown = mutableListOf<Notification>()
    project.messageBus.connect(disposable).subscribe(
        Notifications.TOPIC,
        object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.groupId == RefreshFailureNotifier.GROUP_ID) shown += notification
            }
        },
    )
    return shown
}

/** Makes an event for [action] from the toolbar of [panel]. The data context contains [project] and [panel]. */
fun tasksPanelEvent(action: AnAction, project: Project, panel: MoonTasksPanel): AnActionEvent = TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
)
