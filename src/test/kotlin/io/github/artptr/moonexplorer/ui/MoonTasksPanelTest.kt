package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ClickListener
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.testData.LargeQueryTasksJson
import io.github.artptr.moonexplorer.testData.TestData
import kotlin.system.measureTimeMillis
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class MoonTasksPanelTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        // The light project is the same for all tests. Start each test with the default group mode.
        project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        } finally {
            super.tearDown()
        }
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
        assertEquals("web:build", (panel.tree.selectionPath?.lastPathComponent as TaskNode).task.target)
    }

    private fun loadRecorded() = panel.render(
        TasksState.Loaded(
            QueryTasksParser.parse(TestData.recorded("query-tasks.json"))
                .withStacks(QueryProjectsParser.parseStacks(TestData.recorded("query-projects.json"))),
        ),
    )

    fun testSelectedTaskIsTheSelectedTaskNode() {
        loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        assertEquals("web:build", panel.selectedTask()?.target)
    }

    fun testSelectedTaskIsNullForAProjectNodeOrTwoNodes() {
        loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        assertNull(panel.selectedTask())
        panel.tree.selectionPaths = arrayOf(taskPath("web:build"), taskPath("web:lint"))
        assertNull(panel.selectedTask())
    }

    fun testDoubleClickOnATaskRunsIt() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        loadRecorded()
        assertTrue(panel.onDoubleClick(taskPath("web:build")))
        assertEquals(listOf("web:build"), ran)
    }

    fun testDoubleClickOnAProjectDoesNotRunATask() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        loadRecorded()
        assertFalse("The tree must expand or collapse the project node", panel.onDoubleClick(projectPath("web")))
        assertFalse(panel.onDoubleClick(null))
        assertEmpty(ran)
    }

    fun testDoubleClickListenerIsInstalled() {
        fun clickListeners(tree: Tree) = tree.mouseListeners.count { it.javaClass.name.startsWith(ClickListener::class.java.name) }
        // The Tree class has its own click listener. The panel adds one more.
        assertEquals(clickListeners(Tree()) + 1, clickListeners(panel.tree))
    }

    private fun enterAction(): MoonTasksPanel.RunOnEnterAction =
        ActionUtil.getActions(panel.tree).filterIsInstance<MoonTasksPanel.RunOnEnterAction>().single()

    fun testEnterKeyHasTheTextOfTheRunAction() {
        val enter = enterAction()
        assertTrue(enter.shortcutSet.shortcuts.any { it in CommonShortcuts.ENTER.shortcuts })
        assertEquals(ActionManager.getInstance().getAction(MoonTasksPanel.RUN_TASK_ACTION_ID).templateText, enter.templateText)
    }

    fun testEnterKeyRunsTheSelectedTaskAndClosesTheSpeedSearch() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        val action = enterAction()
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertTrue(event.presentation.isEnabled)
        action.actionPerformed(event)
        assertEquals(listOf("web:build"), ran)
        assertFalse(panel.speedSearch.isPopupActive)
    }

    fun testEnterKeyOnAProjectNodeIsLeftToTheTree() {
        loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        val action = enterAction()
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertFalse("A disabled action does not take the Enter key from the tree", event.presentation.isEnabled)
    }

    private fun topNodeNames() = (panel.tree.model.root as DefaultMutableTreeNode).children().toList().map { it.toString() }

    fun testChangeGroupingShowsTheNewStructure() {
        loadRecorded()
        panel.changeGrouping(TaskGrouping.TASK_NAME)
        assertTrue("lint" in topNodeNames())
        panel.changeGrouping(TaskGrouping.STACK)
        assertEquals(listOf("backend", "frontend"), topNodeNames())
        panel.changeGrouping(TaskGrouping.PROJECT)
        assertEquals(7, topNodeNames().size)
    }

    fun testGroupingIsSavedInTheSettings() {
        panel.changeGrouping(TaskGrouping.STACK)
        assertEquals(TaskGrouping.STACK, project.service<MoonSettings>().taskGrouping)
        // A new panel, for example after a restart of the IDE, uses the saved group mode.
        val other = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, other)
        assertEquals(TaskGrouping.STACK, other.grouping)
    }

    fun testChangeGroupingKeepsTheSelectedTask() {
        loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        panel.changeGrouping(TaskGrouping.TASK_NAME)
        val selected = panel.tree.selectionPath?.lastPathComponent as TaskNode
        assertEquals("web:build", selected.task.target)
        assertEquals("build", selected.parent.toString())
    }

    fun testRefreshKeepsExpandedNodesInStackGrouping() {
        loadRecorded()
        panel.changeGrouping(TaskGrouping.STACK)
        val frontendWeb = panel.allPaths().single { (it.lastPathComponent as? ProjectNode)?.project?.id == "web" }
        panel.tree.expandPath(frontendWeb)
        loadRecorded()
        val again = panel.allPaths().single { (it.lastPathComponent as? ProjectNode)?.project?.id == "web" }
        assertTrue(panel.tree.isExpanded(again))
    }

    fun testChangeGroupingWithAMessageShowsTheMessage() {
        panel.render(TasksState.NoWorkspace)
        panel.changeGrouping(TaskGrouping.STACK)
        assertEquals(MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
    }
}
