package io.github.artptr.moonexplorer.ui

import com.intellij.notification.Notification
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.testData.TestData

class RefreshFailureNotifierTest : BasePlatformTestCase() {

    private lateinit var notifier: RefreshFailureNotifier
    private lateinit var shown: List<Notification>

    private val loaded get() = TestData.recordedState()

    override fun setUp() {
        super.setUp()
        notifier = project.service<RefreshFailureNotifier>()
        shown = collectRefreshNotifications(project, testRootDisposable)
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
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("Error: moon.yml is not valid"))))
        val notification = shown.single()
        assertTrue(notification.content.contains("Error: moon.yml is not valid"))
        assertEquals(MoonExplorerBundle.message("notification.refreshFailed.title"), notification.title)
        assertEquals(listOf(MoonExplorerBundle.message("notification.action.refresh")), notification.actions.map { it.templateText })
    }

    fun testErrorTextIsEscaped() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("<b>bad</b>\nline"))))
        val content = shown.single().content
        assertTrue(content, content.contains("&lt;b&gt;bad&lt;/b&gt;<br>line"))
    }

    fun testNewFailureReplacesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("first"))))
        notifier.onState(TasksState.Loading)
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("second"))))
        assertEquals(2, shown.size)
        assertTrue(shown[0].isExpired)
        assertFalse(shown[1].isExpired)
        assertSame(shown[1], notifier.notification)
    }

    fun testSuccessfulLoadClosesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("bad"))))
        notifier.onState(TasksState.Loading)
        assertFalse("A load must not close the notification", shown.single().isExpired)
        notifier.onState(loaded)
        assertTrue(shown.single().isExpired)
        assertNull(notifier.notification)
    }

    fun testNoWorkspaceClosesTheNotification() {
        notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("bad"))))
        notifier.onState(TasksState.NoWorkspace)
        assertTrue(shown.single().isExpired)
    }

    fun testFailuresThatTheSettingsCanCorrectHaveTheSettingsAction() {
        val causes = listOf(MoonFailure.NotFound(emptyList()), MoonFailure.StartFailed("Cannot run program"), MoonFailure.TimedOut)
        for (cause in causes) {
            notifier.onState(TasksState.Stale(loaded, TasksState.MoonFailed(cause)))
            val actions = notifier.notification!!.actions.map { it.templateText }
            assertTrue("Cause: $cause", MoonExplorerBundle.message("notification.action.openSettings") in actions)
        }
    }

    fun testFailureWithoutAnEarlierLoadShowsNoNotification() {
        notifier.onState(TasksState.MoonFailed(MoonFailure.Error("bad")))
        assertEmpty(shown)
    }
}
