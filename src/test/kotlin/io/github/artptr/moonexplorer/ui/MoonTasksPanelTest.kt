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
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.settings.resetForTests
import io.github.artptr.moonexplorer.testData.LargeQueryProjectsJson
import io.github.artptr.moonexplorer.testData.TestData
import kotlin.system.measureTimeMillis
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class MoonTasksPanelTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        // The light project is the same for all tests. Start each test with the default category and main group.
        project.service<MoonSettings>().resetForTests()
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().resetForTests()
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
        panel.render(TasksState.MoonTimeout)
        panel.render(TasksState.MoonError("bad"))
        assertFalse("The link must not stay after the timeout message", panel.settingsLink.isVisible)
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
        // The user can change the time limit in the settings.
        assertTrue(panel.settingsLink.isVisible)
    }

    fun testInvalidOutputShowsMessage() {
        panel.render(TasksState.InvalidOutput("bad JSON"))
        assertMessage(MoonExplorerBundle.message("tasks.message.invalidOutput", "bad JSON"))
    }

    fun testEmptyWorkspaceShowsMessage() {
        panel.render(TasksState.Loaded(WorkspaceTasks(emptyList()), TestData.workspaceRoot))
        assertMessage(MoonExplorerBundle.message("tasks.message.noTasks"))
    }

    fun testLoadingShowsIndicator() {
        panel.render(TasksState.Loading)
        assertTrue(panel.loadingPanel.isLoading)
    }

    fun testLoadedShowsTree() {
        panel.render(TestData.recordedState())
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertFalse(panel.loadingPanel.isLoading)
    }

    fun testLargeWorkspaceLoadsInLessThanTwoSeconds() {
        val json = LargeQueryProjectsJson.generate()
        val millis = measureTimeMillis {
            panel.render(TasksState.Loaded(QueryProjectsParser.parse(json), TestData.workspaceRoot))
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
        val tasks = TestData.recordedTasks()
        panel.render(TasksState.Loaded(tasks, TestData.workspaceRoot))
        panel.tree.expandPath(projectPath("web"))
        assertTrue(panel.tree.isExpanded(projectPath("web")))

        val changed = tasks.copy(projects = tasks.projects.map { project ->
            project.copy(tasks = project.tasks.map { it.copy(description = "Changed") })
        })
        panel.tree.selectionPath = taskPath("web:build")
        panel.render(TasksState.Loaded(changed, TestData.workspaceRoot))
        assertTrue("The web node must stay expanded", panel.tree.isExpanded(projectPath("web")))
        assertEquals("web:build", (panel.tree.selectionPath?.lastPathComponent as TaskNode).task.target)
    }

    fun testSelectedTaskIsTheSelectedTaskNode() {
        panel.loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        assertEquals("web:build", panel.selectedTask()?.target)
    }

    fun testSelectedTaskIsNullForAProjectNodeOrTwoNodes() {
        panel.loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        assertNull(panel.selectedTask())
        panel.tree.selectionPaths = arrayOf(taskPath("web:build"), taskPath("web:lint"))
        assertNull(panel.selectedTask())
    }

    fun testDoubleClickOnATaskRunsIt() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        panel.loadRecorded()
        assertTrue(panel.onDoubleClick(taskPath("web:build")))
        assertEquals(listOf("web:build"), ran)
    }

    fun testDoubleClickOnAProjectDoesNotRunATask() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        panel.loadRecorded()
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
        panel.loadRecorded()
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
        panel.loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        val action = enterAction()
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)
        assertFalse("A disabled action does not take the Enter key from the tree", event.presentation.isEnabled)
    }

    private fun topNodeNames() = (panel.tree.model.root as DefaultMutableTreeNode).children().toList().map { it.toString() }

    fun testChangeCategoryAndMainGroupShowsTheNewStructure() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertTrue("lint" in topNodeNames())
        panel.changeCategory(TaskCategory.STACK)
        assertEquals(listOf("backend", "frontend", "(none)"), topNodeNames())
        panel.changeCategory(TaskCategory.LAYER)
        assertEquals(listOf("application", "library", "(none)"), topNodeNames())
        panel.changeCategory(TaskCategory.NONE)
        panel.changeMainGroup(TaskGrouping.PROJECT)
        assertEquals(9, topNodeNames().size)
    }

    fun testCategoryAndMainGroupAreSavedInTheSettings() {
        panel.changeCategory(TaskCategory.LAYER)
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertEquals(TaskCategory.LAYER, project.service<MoonSettings>().taskCategory)
        assertEquals(TaskGrouping.TASK_NAME, project.service<MoonSettings>().mainGroup)
        // A new panel, for example after a restart of the IDE, uses the saved values.
        val other = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, other)
        assertEquals(TaskCategory.LAYER, other.category)
        assertEquals(TaskGrouping.TASK_NAME, other.mainGroup)
    }

    fun testChangeMainGroupKeepsTheExpandedCategoryNode() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.STACK)
        val backend = panel.allPaths().single { it.lastPathComponent.toString() == "backend" }
        panel.tree.expandPath(backend)
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        val again = panel.allPaths().single { it.lastPathComponent.toString() == "backend" }
        assertTrue(panel.tree.isExpanded(again))
    }

    fun testChangeMainGroupKeepsTheSelectedTask() {
        panel.loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        val selected = panel.tree.selectionPath?.lastPathComponent as TaskNode
        assertEquals("web:build", selected.task.target)
        assertEquals("build", selected.parent.toString())
    }

    fun testRefreshKeepsExpandedNodesInStackCategory() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.STACK)
        val frontendWeb = panel.allPaths().single { (it.lastPathComponent as? ProjectNode)?.project?.id == "web" }
        panel.tree.expandPath(frontendWeb)
        panel.loadRecorded()
        val again = panel.allPaths().single { (it.lastPathComponent as? ProjectNode)?.project?.id == "web" }
        assertTrue(panel.tree.isExpanded(again))
    }

    fun testChangeCategoryWithAMessageShowsTheMessage() {
        panel.render(TasksState.NoWorkspace)
        panel.changeCategory(TaskCategory.STACK)
        assertEquals(MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
    }

    fun testStaleKeepsTheTreeAndTheExpandedNodes() {
        panel.loadRecorded()
        panel.tree.expandPath(projectPath("web"))
        panel.render(TasksState.Loading)
        val last = TestData.recordedState()
        panel.render(TasksState.Stale(last, TasksState.MoonError("bad config")))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertFalse(panel.loadingPanel.isLoading)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue(panel.tree.isExpanded(projectPath("web")))
        assertTrue("Select Opened File must operate with the last tasks", panel.canSelectProject())
    }

    fun testStaleAfterAMessageShowsTheLastTasks() {
        panel.render(TasksState.MoonError("old"))
        val last = TestData.recordedState()
        panel.render(TasksState.Stale(last, TasksState.MoonTimeout))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
    }

    fun testFailureTextIsTheSameAsTheMessage() {
        panel.render(TasksState.InvalidOutput("bad JSON"))
        assertEquals(failureText(TasksState.InvalidOutput("bad JSON")), panel.messageArea.text)
    }
}
