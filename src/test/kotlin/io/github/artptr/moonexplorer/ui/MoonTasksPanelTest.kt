package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.testData.LargeQueryTasksJson
import io.github.artptr.moonexplorer.testData.TestData
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

    fun testMoonNotFoundShowsMessage() {
        panel.render(TasksState.MoonNotFound)
        assertMessage(MoonExplorerBundle.message("tasks.message.moonNotFound"))
    }

    fun testMoonErrorShowsTheErrorText() {
        panel.render(TasksState.MoonError("Error: moon.yml is not valid"))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonError", "Error: moon.yml is not valid"))
        assertTrue(panel.messageArea.text.contains("Error: moon.yml is not valid"))
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

    fun testRefreshKeepsExpandedNodesWhenADescriptionChanges() {
        val tasks = QueryTasksParser.parse(TestData.recorded("query-tasks.json"))
        panel.render(TasksState.Loaded(tasks))
        panel.tree.expandRow(5) // web
        assertTrue(panel.tree.isExpanded(5))

        val changed = tasks.copy(projects = tasks.projects.map { project ->
            project.copy(tasks = project.tasks.map { it.copy(description = "Changed") })
        })
        panel.tree.setSelectionRow(6) // web:build
        panel.render(TasksState.Loaded(changed))
        assertTrue("The web node must stay expanded", panel.tree.isExpanded(5))
        assertEquals("web:build", panel.tree.selectionPath?.lastPathComponent.toString())
    }
}
