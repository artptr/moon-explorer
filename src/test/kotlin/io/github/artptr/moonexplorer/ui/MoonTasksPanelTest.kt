package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ClickListener
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.OutputProblem
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

    fun testNotFoundShowsTheExaminedLocationsAndTheSettingsLink() {
        val examined = listOf(Path.of("/work/node_modules/.bin/moon"), Path.of("/home/.proto/shims/moon"))
        panel.render(TasksState.MoonFailed(MoonFailure.NotFound(examined)))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonNotFound", examined.joinToString("\n") { "  $it" }))
        examined.forEach { assertTrue(panel.messageArea.text.contains(it.toString())) }
        assertTrue(panel.settingsLink.isVisible)
    }

    fun testOtherMessagesDoNotShowTheSettingsLink() {
        panel.render(TasksState.MoonFailed(MoonFailure.NotFound(emptyList())))
        panel.render(TasksState.MoonFailed(MoonFailure.Error("bad")))
        assertFalse(panel.settingsLink.isVisible)
        panel.render(TasksState.MoonFailed(MoonFailure.TimedOut))
        panel.render(TasksState.MoonFailed(MoonFailure.Error("bad")))
        assertFalse("The link must not stay after the timeout message", panel.settingsLink.isVisible)
    }

    fun testErrorShowsTheErrorText() {
        panel.render(TasksState.MoonFailed(MoonFailure.Error("Error: moon.yml is not valid")))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonError", "Error: moon.yml is not valid"))
        assertTrue(panel.messageArea.text.contains("Error: moon.yml is not valid"))
    }

    fun testTimedOutShowsMessage() {
        panel.render(TasksState.Loading)
        panel.render(TasksState.MoonFailed(MoonFailure.TimedOut))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonTimeout"))
        // The user can change the time limit in the settings.
        assertTrue(panel.settingsLink.isVisible)
    }

    fun testExitCodeShowsMessage() {
        panel.render(TasksState.MoonFailed(MoonFailure.ExitCode(3)))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonExitCode", "3"))
        assertFalse(panel.settingsLink.isVisible)
    }

    fun testCancelledShowsMessage() {
        panel.render(TasksState.MoonFailed(MoonFailure.Cancelled))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonCancelled"))
        assertFalse(panel.settingsLink.isVisible)
    }

    fun testStartFailedShowsTheDetailAndTheSettingsLink() {
        panel.render(TasksState.MoonFailed(MoonFailure.StartFailed("Cannot run program")))
        assertMessage(MoonExplorerBundle.message("tasks.message.moonStartFailed", "Cannot run program"))
        // Usually, the plugin cannot start moon because the path to moon in the settings is not correct.
        assertTrue(panel.settingsLink.isVisible)
    }

    fun testStartFailedWithoutDetailShowsMessage() {
        for (detail in listOf(null, " ")) {
            panel.render(TasksState.MoonFailed(MoonFailure.StartFailed(detail)))
            assertMessage(MoonExplorerBundle.message("tasks.message.moonStartFailedNoDetail"))
            assertTrue(panel.settingsLink.isVisible)
        }
    }

    fun testOutputThatIsNotJsonShowsTheDetail() {
        panel.render(TasksState.InvalidOutput(OutputProblem.NotJson("line 1 column 7")))
        assertMessage(MoonExplorerBundle.message("tasks.message.invalidOutput.notJson", "line 1 column 7"))
    }

    fun testOutputThatIsNotJsonWithoutDetailShowsMessage() {
        panel.render(TasksState.InvalidOutput(OutputProblem.NotJson(null)))
        assertMessage(MoonExplorerBundle.message("tasks.message.invalidOutput.notJsonNoDetail"))
    }

    fun testOutputWithoutProjectsShowsMessage() {
        panel.render(TasksState.InvalidOutput(OutputProblem.NoProjects))
        assertMessage(MoonExplorerBundle.message("tasks.message.invalidOutput.noProjects"))
    }

    fun testUnexpectedErrorShowsMessage() {
        panel.render(TasksState.UnexpectedError)
        assertMessage(MoonExplorerBundle.message("tasks.message.unexpectedError"))
        assertFalse(panel.settingsLink.isVisible)
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

    fun testSelectedTasksAreTheSelectedTaskNodes() {
        panel.loadRecorded()
        panel.tree.selectionPaths = arrayOf(taskPath("web:build"), taskPath("api:audit"))
        assertEquals(listOf("web:build", "api:audit"), panel.selectedTasks()?.map { it.target })
        assertNull(panel.selectedTaskNameTasks())
    }

    fun testSelectedTasksAreNullForOneTaskOrAMixedSelection() {
        panel.loadRecorded()
        panel.tree.selectionPath = taskPath("web:build")
        assertNull(panel.selectedTasks())
        panel.tree.selectionPaths = arrayOf(taskPath("web:build"), projectPath("api"))
        assertNull(panel.selectedTasks())
    }

    private fun namePath(category: String?, name: String): TreePath = panel.allPaths().single { path ->
        val node = path.lastPathComponent
        node is TaskNameNode && node.name == name && (category == null || path.parentPath.lastPathComponent.toString() == category)
    }

    fun testSelectedTaskNameTasksAreAllProjectsWithTheTask() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.tree.selectionPath = namePath(null, "audit")
        assertEquals(listOf("api:audit", "db:audit", "worker:audit"), panel.selectedTaskNameTasks()?.map { it.target })
        assertNull(panel.selectedTasks())
        assertNull(panel.selectedTask())
    }

    fun testSelectedTaskNameTasksInACategoryAreOnlyTheProjectsOfTheCategory() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = namePath("library", "build")
        assertEquals(listOf("db:build", "ui:build", "utils:build"), panel.selectedTaskNameTasks()?.map { it.target })
    }

    /** Gives the value of [key] in the data snapshot of the panel. The actions of the context menu get their data from this snapshot. */
    private fun <T : Any> treeData(key: DataKey<T>): T? =
        CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { panel.uiDataSnapshot(it) }.getData(key)

    fun testDataContextHasTheSelectedTasks() {
        panel.loadRecorded()
        panel.tree.selectionPaths = arrayOf(taskPath("web:build"), taskPath("api:audit"))
        assertEquals(listOf("web:build", "api:audit"), treeData(SELECTED_TASKS)?.map { it.target })
        assertNull(treeData(SELECTED_TASK_NAME_TASKS))
    }

    fun testDataContextHasTheTasksOfTheSelectedTaskName() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.tree.selectionPath = namePath(null, "audit")
        assertEquals(listOf("api:audit", "db:audit", "worker:audit"), treeData(SELECTED_TASK_NAME_TASKS)?.map { it.target })
        assertNull(treeData(SELECTED_TASKS))
    }

    fun testDataContextHasTheSelectedProjectAndTheShownTasks() {
        assertNull(treeData(SHOWN_TASKS))
        panel.loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        assertEquals("web", treeData(SELECTED_PROJECT)?.id)
        assertEquals(TestData.recordedState(), treeData(SHOWN_TASKS))
        panel.tree.selectionPath = taskPath("web:build")
        assertNull(treeData(SELECTED_PROJECT))
        panel.tree.selectionPaths = arrayOf(projectPath("web"), projectPath("api"))
        assertNull(treeData(SELECTED_PROJECT))
    }

    /** Gives true if the registered action [id] is visible in the context menu of the selected nodes. The action gets the data of the panel. */
    private fun isInContextMenu(id: String): Boolean {
        val action = ActionManager.getInstance().getAction(id)
        val context = CustomizedDataContext.withSnapshot(SimpleDataContext.getProjectContext(project)) { panel.uiDataSnapshot(it) }
        return TestActionEvent.createTestEvent(action, context).also(action::update).presentation.isEnabledAndVisible
    }

    fun testContextMenuOfAProjectNodeAndATaskNodeHasJumpToSource() {
        panel.loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        assertTrue(isInContextMenu("MoonExplorer.JumpToSource"))
        panel.tree.selectionPath = taskPath("web:lint")
        assertTrue(isInContextMenu("MoonExplorer.JumpToSource"))
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = panel.allPaths().first { it.lastPathComponent is CategoryNode }
        assertFalse(isInContextMenu("MoonExplorer.JumpToSource"))
    }

    fun testShownTasksAreTheOldTasksDuringALoad() {
        panel.loadRecorded()
        panel.render(TasksState.Loading)
        assertEquals(TestData.recordedState(), treeData(SHOWN_TASKS))
        panel.render(TasksState.NoWorkspace)
        assertNull(treeData(SHOWN_TASKS))
    }

    fun testCategoryAndProjectNodesHaveNoTaskLists() {
        panel.loadRecorded()
        panel.tree.selectionPath = projectPath("web")
        assertNull(panel.selectedTaskNameTasks())
        assertNull(panel.selectedTasks())
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = panel.allPaths().single { it.lastPathComponent.toString() == "library" }
        assertNull(panel.selectedTaskNameTasks())
        assertNull(panel.selectedTasks())
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
        panel.render(TasksState.Stale(last, TasksState.MoonFailed(MoonFailure.Error("bad config"))))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertFalse(panel.loadingPanel.isLoading)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue(panel.tree.isExpanded(projectPath("web")))
        assertTrue("Select Opened File must operate with the last tasks", panel.canSelectProject())
    }

    fun testStaleAfterAMessageShowsTheLastTasks() {
        panel.render(TasksState.MoonFailed(MoonFailure.Error("old")))
        val last = TestData.recordedState()
        panel.render(TasksState.Stale(last, TasksState.MoonFailed(MoonFailure.TimedOut)))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
    }

    fun testFailureTextIsTheSameAsTheMessage() {
        panel.render(TasksState.InvalidOutput(OutputProblem.NoProjects))
        assertEquals(failureText(TasksState.InvalidOutput(OutputProblem.NoProjects)), panel.messageArea.text)
    }

    fun testEachStateUpdatesTheToolbar() {
        // The action ShowRefreshFailureAction must hide when a new refresh starts after a failure.
        var updates = 0
        panel.updateToolbar = { updates++ }
        val states = listOf(
            TestData.recordedState(),
            TasksState.Stale(TestData.recordedState(), TasksState.MoonFailed(MoonFailure.Error("bad config"))),
            TasksState.Loading,
            TasksState.NoWorkspace,
            TasksState.MoonFailed(MoonFailure.TimedOut),
        )
        states.forEach(panel::render)
        assertEquals(states.size, updates)
    }
}
