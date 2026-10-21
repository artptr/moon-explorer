package io.github.artptr.moonexplorer.ui

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.testData.TestData

class RefreshFailureNotifierTest : BasePlatformTestCase() {

    private lateinit var notifier: RefreshFailureNotifier
    private val shown = mutableListOf<Notification>()

    private val loaded get() = TasksState.Loaded(TestData.recordedTasks(), TestData.workspaceRoot)

    override fun setUp() {
        super.setUp()
        notifier = project.service<RefreshFailureNotifier>()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == RefreshFailureNotifier.GROUP_ID) shown += notification
                }
            },
        )
    }

    override fun tearDown() {
        try {
            // The light project is the same for all tests. Close the notification of this test.
            notifier.onState(loaded)
        } finally {
            super.tearDown()
        }
    }

    fun testStaleShowsANotificationWithTheMoonError() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("Error: moon.yml is not valid")))
        val notification = shown.single()
        assertTrue(notification.content.contains("Error: moon.yml is not valid"))
        assertEquals(MoonExplorerBundle.message("notification.refreshFailed.title"), notification.title)
        assertEquals(listOf(MoonExplorerBundle.message("notification.action.refresh")), notification.actions.map { it.templateText })
    }

    fun testErrorTextIsEscaped() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("<b>bad</b>\nline")))
        val content = shown.single().content
        assertTrue(content, content.contains("&lt;b&gt;bad&lt;/b&gt;<br>line"))
    }

    fun testNewFailureReplacesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("first")))
        notifier.onState(TasksState.Loading)
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("second")))
        assertEquals(2, shown.size)
        assertTrue(shown[0].isExpired)
        assertFalse(shown[1].isExpired)
        assertSame(shown[1], notifier.notification)
    }

    fun testSuccessfulLoadClosesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("bad")))
        notifier.onState(TasksState.Loading)
        assertFalse("A load must not close the notification", shown.single().isExpired)
        notifier.onState(loaded)
        assertTrue(shown.single().isExpired)
        assertNull(notifier.notification)
    }

    fun testNoWorkspaceClosesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonError("bad")))
        notifier.onState(TasksState.NoWorkspace)
        assertTrue(shown.single().isExpired)
    }

    fun testMoonNotFoundHasTheSettingsAction() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonNotFound(emptyList())))
        val actions = shown.single().actions.map { it.templateText }
        assertTrue(MoonExplorerBundle.message("notification.action.openSettings") in actions)
    }

    fun testFailureWithoutAnEarlierLoadShowsNoNotification() {
        notifier.onState(TasksState.MoonError("bad"))
        assertEmpty(shown)
    }
}
