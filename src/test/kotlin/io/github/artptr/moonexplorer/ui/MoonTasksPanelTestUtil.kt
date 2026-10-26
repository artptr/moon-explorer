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
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.testData.TestData
import java.awt.Point
import javax.swing.tree.TreePath

/** Shows the recorded tasks of the test workspace in the panel. Call this function on the EDT. */
fun MoonTasksPanel.loadRecorded() = render(TestData.recordedState())

/** Gives the paths of all nodes below the root, also the nodes in collapsed nodes. Each parent node is before its child nodes. */
fun MoonTasksPanel.allPaths(): List<TreePath> = pathsOf { true }

/** Gives the paths of the selected nodes. */
fun MoonTasksPanel.selectedPaths(): List<TreePath> = tree.selectionPaths.orEmpty().toList()

/** Gives the path of the category node [value]. */
fun MoonTasksPanel.categoryPath(value: String): TreePath = allPaths().single { (it.lastPathComponent as? CategoryNode)?.value == value }

/** Gives the path of the node below the category node [value] for which [node] gives true. */
private fun MoonTasksPanel.pathIn(value: String, node: (Any) -> Boolean): TreePath = allPaths().single { path ->
    node(path.lastPathComponent) && (path.getPathComponent(1) as CategoryNode).value == value
}

/** Gives the path of the task node [target] below the category node [value]. */
fun MoonTasksPanel.taskPathIn(value: String, target: String): TreePath = pathIn(value) { (it as? TaskNode)?.task?.target == target }

/** Gives the path of the project node [id] below the category node [value]. */
fun MoonTasksPanel.projectPathIn(value: String, id: String): TreePath = pathIn(value) { (it as? ProjectNode)?.project?.id == id }

/**
 * Sets the size of the panel and lays out its components. Then the visible area of the tree shows [rows] rows.
 * A panel in a test is not on the screen. Thus the tree has no visible area before a call of this function.
 */
fun MoonTasksPanel.layOut(rows: Int) {
    setSize(LAYOUT_WIDTH, LAYOUT_WIDTH * 4)
    UIUtil.uiTraverser(this).forEach { it.doLayout() }
    val viewport = ComponentUtil.getViewport(tree)!!
    setSize(LAYOUT_WIDTH, height - viewport.height + rows * tree.getRowBounds(0).height)
    UIUtil.uiTraverser(this).forEach { it.doLayout() }
}

private const val LAYOUT_WIDTH = 400

/** Scrolls the tree. Then the node of [path] is at the top of the visible area. Call [layOut] first. */
fun MoonTasksPanel.scrollToTop(path: TreePath) {
    ComponentUtil.getViewport(tree)!!.viewPosition = Point(0, tree.getPathBounds(path)!!.y)
}

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
