package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.testData.LargeQueryTasksJson
import io.github.artptr.moonexplorer.testData.TestData
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.system.measureTimeMillis

class MoonTasksPanelTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    private fun assertMessage(expected: String) {
        assertEquals(MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
        assertEquals(expected, panel.messageArea.text)
        assertFalse(panel.loadingPanel.isLoading)
    }

    fun testNoWorkspaceShowsMessage() {
        panel.render(TasksState.NoWorkspace)
        assertMessage(MoonExplorerBundle.message("tasks.message.noWorkspace"))
    }

    fun testMoonNotFoundShowsTheExaminedLocationsAndTheSettingsLink() {
        val examined = listOf(Path.of("/work/node_modules/.bin/moon"), Path.of("/home/.proto/shims/moon"))
        panel.render(TasksState.MoonNotFound(examined))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonNotFound", examined.joinToString("\n") { "  $it" }))
        examined.forEach { assertTrue(panel.messageArea.text.contains(it.toString())) }
        assertTrue(panel.settingsLink.isVisible)
    }

    fun testOtherMessagesDoNotShowTheSettingsLink() {
        panel.render(TasksState.MoonNotFound(emptyList()))
        panel.render(TasksState.MoonError("bad"))
        assertFalse(panel.settingsLink.isVisible)
    }

    fun testMoonErrorShowsTheErrorText() {
        panel.render(TasksState.MoonError("Error: moon.yml is not valid"))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonError", "Error: moon.yml is not valid"))
        assertTrue(panel.messageArea.text.contains("Error: moon.yml is not valid"))
    }

    fun testMoonTimeoutShowsMessage() {
        panel.render(TasksState.Loading)
        panel.render(TasksState.MoonTimeout)
        assertMessage(MoonExplorerBundle.message("tasks.message.moonTimeout"))
    }

    fun testInvalidOutputShowsMessage() {
        panel.render(TasksState.InvalidOutput("bad JSON"))
        assertMessage(MoonExplorerBundle.message("tasks.message.invalidOutput", "bad JSON"))
    }

    fun testEmptyWorkspaceShowsMessage() {
        panel.render(TasksState.Loaded(WorkspaceTasks(emptyList())))
        assertMessage(MoonExplorerBundle.message("tasks.message.noTasks"))
    }

    fun testLoadingShowsIndicator() {
        panel.render(TasksState.Loading)
        assertTrue(panel.loadingPanel.isLoading)
    }

    fun testLoadedShowsTree() {
        panel.render(TasksState.Loaded(QueryTasksParser.parse(TestData.recorded("query-tasks.json"))))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertEquals(7, panel.tree.model.getChildCount(panel.tree.model.root))
        assertFalse(panel.loadingPanel.isLoading)
    }

    fun testLargeWorkspaceLoadsInLessThanTwoSeconds() {
        val json = LargeQueryTasksJson.generate()
        val millis = measureTimeMillis {
            panel.render(TasksState.Loaded(QueryTasksParser.parse(json)))
        }
        assertEquals(17, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue("The tree loaded in $millis ms", millis < 2_000)
    }

    private fun projectNode(id: String): ProjectNode {
        val root = panel.tree.model.root as DefaultMutableTreeNode
        return root.children().toList().filterIsInstance<ProjectNode>().single { it.project.id == id }
    }

    private fun projectPath(id: String) = TreePath(projectNode(id).path)

    private fun taskPath(target: String): TreePath {
        val task = projectNode(target.substringBefore(':')).children().toList()
            .filterIsInstance<TaskNode>().single { it.task.target == target }
        return TreePath(task.path)
    }

    fun testRefreshKeepsExpandedNodesWhenADescriptionChanges() {
        val tasks = QueryTasksParser.parse(TestData.recorded("query-tasks.json"))
        panel.render(TasksState.Loaded(tasks))
        panel.tree.expandPath(projectPath("web"))
        assertTrue(panel.tree.isExpanded(projectPath("web")))

        val changed = tasks.copy(projects = tasks.projects.map { project ->
            project.copy(tasks = project.tasks.map { it.copy(description = "Changed") })
        })
        panel.tree.selectionPath = taskPath("web:build")
        panel.render(TasksState.Loaded(changed))
        assertTrue("The web node must stay expanded", panel.tree.isExpanded(projectPath("web")))
        assertEquals("web:build", panel.tree.selectionPath?.lastPathComponent.toString())
    }
}
