package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.ClickListener
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.*
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.testData.LargeQueryProjectsJson
import io.github.artptr.moonexplorer.testData.TestData
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.system.measureTimeMillis

class MoonTasksPanelTest : MoonTasksPanelTestCase() {

    fun testEachStateWithoutTasksShowsItsMessageAndTheSettingsLinkIfItHelps() {
        val examined = listOf(Path.of("/work/node_modules/.bin/moon"), Path.of("/home/.proto/shims/moon"))
        fun failed(cause: MoonFailure) = TasksState.MoonFailed(cause)
        fun message(key: String, vararg params: String) = MoonExplorerBundle.message(key, *params)
        // Each row is a state, the expected message, and true if the message has the settings link.
        // The settings link is for the failures that a change of the path to moon or of the time limit can repair.
        val rows = listOf(
            Triple(TasksState.NoWorkspace, message("tasks.message.noWorkspace"), false),
            Triple(failed(MoonFailure.NotFound(examined)), message("tasks.message.moonNotFound", examined.joinToString("\n") { "  $it" }), true),
            Triple(failed(MoonFailure.Error("Error: moon.yml is not valid")), message("tasks.message.moonError", "Error: moon.yml is not valid"), false),
            Triple(failed(MoonFailure.TimedOut), message("tasks.message.moonTimeout"), true),
            Triple(failed(MoonFailure.ExitCode(3)), message("tasks.message.moonExitCode", "3"), false),
            Triple(failed(MoonFailure.Cancelled), message("tasks.message.moonCancelled"), false),
            Triple(failed(MoonFailure.StartFailed("Cannot run program")), message("tasks.message.moonStartFailed", "Cannot run program"), true),
            Triple(failed(MoonFailure.StartFailed(null)), message("tasks.message.moonStartFailedNoDetail"), true),
            Triple(failed(MoonFailure.StartFailed(" ")), message("tasks.message.moonStartFailedNoDetail"), true),
            Triple(TasksState.InvalidOutput(OutputProblem.NotJson("line 1 column 7")), message("tasks.message.invalidOutput.notJson", "line 1 column 7"), false),
            Triple(TasksState.InvalidOutput(OutputProblem.NotJson(null)), message("tasks.message.invalidOutput.notJsonNoDetail"), false),
            Triple(TasksState.InvalidOutput(OutputProblem.NoProjects), message("tasks.message.invalidOutput.noProjects"), false),
            Triple(TasksState.UnexpectedError, message("tasks.message.unexpectedError"), false),
            Triple(TestData.loadedState(WorkspaceTasks(emptyList())), message("tasks.message.noTasks"), false),
        )
        for ((state, text, settingsLink) in rows) {
            panel.render(state)
            assertEquals("Card for $state", MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
            assertEquals("Message for $state", text, panel.messageArea.text)
            assertFalse("Loading indicator for $state", panel.loadingPanel.isLoading)
            assertEquals("Settings link for $state", settingsLink, panel.settingsLink.isVisible)
        }
    }

    fun testOtherMessagesDoNotShowTheSettingsLink() {
        panel.render(TasksState.MoonFailed(MoonFailure.NotFound(emptyList())))
        panel.render(TasksState.MoonFailed(MoonFailure.Error("bad")))
        assertFalse(panel.settingsLink.isVisible)
        panel.render(TasksState.MoonFailed(MoonFailure.TimedOut))
        panel.render(TasksState.MoonFailed(MoonFailure.Error("bad")))
        assertFalse("The link must not stay after the timeout message", panel.settingsLink.isVisible)
    }

    fun testLoadingShowsIndicator() {
        panel.render(TasksState.Loading(null))
        assertTrue(panel.loadingPanel.isLoading)
    }

    fun testRefreshKeepsTheTreeAndTheExpandedNodes() {
        panel.loadRecorded()
        panel.tree.expandPath(panel.projectPath("web"))
        panel.render(TasksState.Loading(TestData.recordedState()))
        assertTrue(panel.loadingPanel.isLoading)
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue(panel.tree.isExpanded(panel.projectPath("web")))
    }

    fun testPanelThatOpensDuringARefreshShowsTheLastTasks() {
        panel.render(TasksState.Loading(TestData.recordedState()))
        assertTrue(panel.loadingPanel.isLoading)
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertEquals(TestData.recordedState(), treeData(SHOWN_TASKS))
        panel.render(TasksState.NoWorkspace)
        assertNull(treeData(SHOWN_TASKS))
    }

    fun testRefreshAfterAnEmptyWorkspaceKeepsTheMessage() {
        val empty = TestData.loadedState(WorkspaceTasks(emptyList()))
        panel.render(empty)
        panel.render(TasksState.Loading(empty))
        assertEquals(MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
        assertEquals(MoonExplorerBundle.message("tasks.message.noTasks"), panel.messageArea.text)
        assertNull(treeData(SHOWN_TASKS))
    }

    fun testLoadedShowsTree() {
        panel.render(TestData.recordedState())
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertFalse(panel.loadingPanel.isLoading)
    }

    fun testLargeWorkspaceLoadsInLessThanTwoSeconds() {
        val tasks = QueryProjectsParser.parse(LargeQueryProjectsJson.generate())
        // The size of the example workspace in the product vision.
        assertEquals(17, tasks.projects.size)
        assertTrue(tasks.projects.all { it.tasks.size == 14 })
        val millis = measureTimeMillis {
            panel.render(TestData.loadedState(tasks))
        }
        assertEquals(17, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue("The tree loaded in $millis ms", millis < 2_000)
    }

    fun testRefreshKeepsExpandedNodesWhenADescriptionChanges() {
        val tasks = TestData.recordedTasks()
        panel.render(TestData.loadedState(tasks))
        panel.tree.expandPath(panel.projectPath("web"))
        assertTrue(panel.tree.isExpanded(panel.projectPath("web")))

        val changed = tasks.copy(projects = tasks.projects.map { project ->
            project.copy(tasks = project.tasks.map { it.copy(description = "Changed") })
        })
        panel.tree.selectionPath = panel.taskPath("web:build")
        panel.render(TestData.loadedState(changed))
        assertTrue("The web node must stay expanded", panel.tree.isExpanded(panel.projectPath("web")))
        assertEquals("web:build", (panel.tree.selectionPath?.lastPathComponent as TaskNode).task.target)
    }

    fun testSelectedTaskIsTheSelectedTaskNode() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.taskPath("web:build")
        assertEquals("web:build", panel.selectedTask()?.target)
    }

    fun testSelectedTaskIsNullForAProjectNodeOrTwoNodes() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.projectPath("web")
        assertNull(panel.selectedTask())
        panel.tree.selectionPaths = arrayOf(panel.taskPath("web:build"), panel.taskPath("web:lint"))
        assertNull(panel.selectedTask())
    }

    fun testSelectedTasksAreNullForOneTaskOrAMixedSelection() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.taskPath("web:build")
        assertNull(panel.selectedTasks())
        panel.tree.selectionPaths = arrayOf(panel.taskPath("web:build"), panel.projectPath("api"))
        assertNull(panel.selectedTasks())
    }

    private fun namePath(category: String?, name: String): TreePath = panel.allPaths().single { path ->
        val node = path.lastPathComponent
        node is TaskNameNode && node.name == name && (category == null || path.parentPath.lastPathComponent.toString() == category)
    }

    fun testSelectedTaskNameTasksInACategoryAreOnlyTheProjectsOfTheCategory() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = namePath("library", "build")
        assertEquals(listOf("db:build", "ui:build", "utils:build"), panel.selectedTaskNameTasks()?.map { it.target })
    }

    fun testTaskNameNodesWithTheSameNameInTwoTagNodesAreOneList() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.changeCategory(TaskCategory.TAG)
        // The node internal contains admin and worker. The node react contains admin, ui, and web.
        panel.tree.selectionPaths = arrayOf(namePath("internal", "build"), namePath("react", "build"))
        assertEquals(listOf("admin:build", "ui:build", "web:build", "worker:build"), panel.selectedTaskNameTasks()?.map { it.target })
        panel.tree.addSelectionPath(namePath("react", "lint"))
        assertNull("Task name nodes with different names have no task list", panel.selectedTaskNameTasks())
    }

    /**
     * Gives a data context with the data of [base] and the data snapshot of the panel.
     * The actions of the context menu get their data from this snapshot.
     */
    private fun panelData(base: DataContext = DataContext.EMPTY_CONTEXT): DataContext =
        CustomizedDataContext.withSnapshot(base) { panel.uiDataSnapshot(it) }

    /** Gives the value of [key] in the data snapshot of the panel. */
    private fun <T : Any> treeData(key: DataKey<T>): T? = panelData().getData(key)

    fun testDataContextHasTheSelectedTasks() {
        panel.loadRecorded()
        panel.tree.selectionPaths = arrayOf(panel.taskPath("web:build"), panel.taskPath("api:audit"))
        assertEquals(listOf("web:build", "api:audit"), treeData(SELECTED_TASKS)?.map { it.target })
        assertNull(treeData(SELECTED_TASK_NAME_TASKS))
    }

    fun testDataContextHasTheTasksOfTheSelectedTaskName() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.tree.selectionPath = namePath(null, "audit")
        assertEquals(listOf("api:audit", "db:audit", "worker:audit"), treeData(SELECTED_TASK_NAME_TASKS)?.map { it.target })
        assertNull(treeData(SELECTED_TASKS))
        assertNull(panel.selectedTask())
    }

    fun testDataContextHasTheSelectedProjectAndTheShownTasks() {
        assertNull(treeData(SHOWN_TASKS))
        panel.loadRecorded()
        panel.tree.selectionPath = panel.projectPath("web")
        assertEquals("web", treeData(SELECTED_PROJECT)?.id)
        assertEquals(TestData.recordedState(), treeData(SHOWN_TASKS))
        panel.tree.selectionPath = panel.taskPath("web:build")
        assertNull(treeData(SELECTED_PROJECT))
        panel.tree.selectionPaths = arrayOf(panel.projectPath("web"), panel.projectPath("api"))
        assertNull(treeData(SELECTED_PROJECT))
    }

    /** Gives true if the registered action [id] is visible in the context menu of the selected nodes. The action gets the data of the panel. */
    private fun isInContextMenu(id: String): Boolean {
        val action = ActionManager.getInstance().getAction(id)
        val context = panelData(SimpleDataContext.getProjectContext(project))
        return TestActionEvent.createTestEvent(action, context).also(action::update).presentation.isEnabledAndVisible
    }

    fun testContextMenuOfAProjectNodeAndATaskNodeHasJumpToSource() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.projectPath("web")
        assertTrue(isInContextMenu("MoonExplorer.JumpToSource"))
        panel.tree.selectionPath = panel.taskPath("web:lint")
        assertTrue(isInContextMenu("MoonExplorer.JumpToSource"))
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = panel.allPaths().first { it.lastPathComponent is CategoryNode }
        assertFalse(isInContextMenu("MoonExplorer.JumpToSource"))
    }

    fun testCategoryAndProjectNodesHaveNoTaskLists() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.projectPath("web")
        assertNull(panel.selectedTaskNameTasks())
        assertNull(panel.selectedTasks())
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        panel.changeCategory(TaskCategory.LAYER)
        panel.tree.selectionPath = panel.categoryPath("library")
        assertNull(panel.selectedTaskNameTasks())
        assertNull(panel.selectedTasks())
        assertNull(panel.selectedTask())
    }

    fun testDoubleClickOnATaskRunsIt() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        panel.loadRecorded()
        assertTrue(panel.onDoubleClick(panel.taskPath("web:build")))
        assertEquals(listOf("web:build"), ran)
    }

    fun testDoubleClickOnAProjectDoesNotRunATask() {
        val ran = mutableListOf<String>()
        panel.runTask = { ran += it.target }
        panel.loadRecorded()
        assertFalse("The tree must expand or collapse the project node", panel.onDoubleClick(panel.projectPath("web")))
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
        panel.tree.selectionPath = panel.taskPath("web:build")
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
        panel.tree.selectionPath = panel.projectPath("web")
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
        panel.changeCategory(TaskCategory.TAG)
        assertEquals(listOf("internal", "node", "public", "react", "shared", "(none)"), topNodeNames())
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
        val backend = panel.categoryPath("backend")
        panel.tree.expandPath(backend)
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        val again = panel.categoryPath("backend")
        assertTrue(panel.tree.isExpanded(again))
    }

    fun testChangeMainGroupKeepsTheSelectedTask() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.taskPath("web:build")
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        val selected = panel.tree.selectionPath?.lastPathComponent as TaskNode
        assertEquals("web:build", selected.task.target)
        assertEquals("build", selected.parent.toString())
    }

    fun testRefreshKeepsExpandedNodesInStackCategory() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.STACK)
        val frontendWeb = panel.projectPath("web")
        panel.tree.expandPath(frontendWeb)
        panel.loadRecorded()
        val again = panel.projectPath("web")
        assertTrue(panel.tree.isExpanded(again))
    }

    fun testChangeToTagCategorySelectsTheFirstNodeOfTheTask() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.taskPath("web:build")
        panel.changeCategory(TaskCategory.TAG)
        // web has the tags react and public. The tree shows web:build in the two tag nodes.
        assertEquals(listOf(panel.taskPathIn("public", "web:build")), panel.selectedPaths())
        assertEquals("web:build", panel.selectedTask()?.target)
        assertTrue(panel.tree.isExpanded(panel.categoryPath("public")))
        assertFalse("The tree must not expand all tag nodes of the task", panel.tree.isExpanded(panel.categoryPath("react")))
    }

    fun testChangeToTagCategorySelectsTheProjectAgain() {
        panel.loadRecorded()
        panel.tree.selectionPath = panel.projectPath("web")
        panel.changeCategory(TaskCategory.TAG)
        assertEquals(listOf(panel.projectPathIn("public", "web")), panel.selectedPaths())
        assertEquals("web", panel.selectedProject()?.id)
    }

    fun testRefreshPrefersTheNodeOfTheTaskThatTheUserCanSee() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.expandPath(panel.projectPathIn("react", "web"))
        panel.tree.selectionPath = panel.taskPathIn("public", "web:build")
        // Change the tags of web to internal and react. The node internal is before the node react, but the user cannot see web below it.
        renderWithWebTags("internal", "react")
        assertEquals(listOf(panel.taskPathIn("react", "web:build")), panel.selectedPaths())
    }

    /** Shows the recorded tasks, but gives the project web the tags [tags]. */
    private fun renderWithWebTags(vararg tags: String) {
        val tasks = TestData.recordedTasks()
        val changed = tasks.copy(projects = tasks.projects.map { if (it.id == "web") it.copy(tags = tags.toSet()) else it })
        panel.render(TestData.loadedState(changed))
    }

    fun testTwoNodesOfTheSameTaskAreOneTask() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.selectionPaths = arrayOf(panel.taskPathIn("public", "web:build"), panel.taskPathIn("react", "web:build"))
        assertEquals("web:build", panel.selectedTask()?.target)
        assertNull(panel.selectedTasks())
        panel.tree.addSelectionPath(panel.taskPathIn("react", "ui:build"))
        assertNull(panel.selectedTask())
        assertEquals(listOf("web:build", "ui:build"), panel.selectedTasks()?.map { it.target })
    }

    fun testRefreshKeepsExpandedNodesInTagCategory() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.expandPath(panel.projectPathIn("react", "web"))
        panel.loadRecorded()
        assertTrue(panel.tree.isExpanded(panel.projectPathIn("react", "web")))
        assertFalse(panel.tree.isExpanded(panel.projectPathIn("public", "web")))
    }

    fun testTwoNodesOfTheSameProjectAreOneProject() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.selectionPaths = arrayOf(panel.projectPathIn("public", "web"), panel.projectPathIn("react", "web"))
        assertEquals("web", panel.selectedProject()?.id)
        panel.tree.addSelectionPath(panel.projectPathIn("react", "ui"))
        assertNull(panel.selectedProject())
    }

    fun testRefreshKeepsTheSelectedNodeOfATaskInOneTagNode() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.selectionPath = panel.taskPathIn("react", "web:build")
        panel.loadRecorded()
        assertEquals(listOf(panel.taskPathIn("react", "web:build")), panel.selectedPaths())
    }

    fun testRefreshSelectsATaskAgainAfterAChangeOfItsTags() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.selectionPaths = arrayOf(panel.taskPathIn("react", "web:build"), panel.taskPathIn("react", "ui:build"))
        // Remove the tag react from web. Then the tree shows web:build only in the node public.
        renderWithWebTags("public")
        val expected = listOf(panel.taskPathIn("react", "ui:build"), panel.taskPathIn("public", "web:build"))
        assertEquals(expected, panel.selectedPaths())
        assertEquals(listOf("ui:build", "web:build"), panel.selectedTasks()?.map { it.target })
    }

    fun testChangeMainGroupKeepsTheTagNodeOfTheSelectedTask() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        // web has the tags public and react. The node public is before the node react.
        panel.tree.selectionPath = panel.taskPathIn("react", "web:build")
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertEquals(listOf(panel.taskPathIn("react", "web:build")), panel.selectedPaths())
        assertFalse("The tree must not expand a different tag node", panel.tree.isExpanded(panel.categoryPath("public")))
        panel.changeMainGroup(TaskGrouping.PROJECT)
        assertEquals(listOf(panel.taskPathIn("react", "web:build")), panel.selectedPaths())
    }

    fun testRefreshSelectsAProjectAndItsTaskInOneTagNode() {
        panel.loadRecorded()
        panel.changeCategory(TaskCategory.TAG)
        panel.tree.expandPath(panel.categoryPath("public"))
        panel.tree.selectionPaths = arrayOf(panel.projectPathIn("react", "web"), panel.taskPathIn("react", "web:build"))
        // Change the tags of web to internal and public. The user can see the project web below public, but not its tasks.
        renderWithWebTags("internal", "public")
        val expected = listOf(panel.projectPathIn("public", "web"), panel.taskPathIn("public", "web:build"))
        assertEquals(expected, panel.selectedPaths())
        assertFalse(panel.tree.isExpanded(panel.categoryPath("internal")))
    }

    fun testChangeCategoryWithAMessageShowsTheMessage() {
        panel.render(TasksState.NoWorkspace)
        panel.changeCategory(TaskCategory.STACK)
        assertEquals(MoonTasksPanel.MESSAGE_CARD, panel.visibleCard)
    }

    fun testStaleKeepsTheTreeAndTheExpandedNodes() {
        panel.loadRecorded()
        panel.tree.expandPath(panel.projectPath("web"))
        panel.render(TasksState.Loading(TestData.recordedState()))
        val last = TestData.recordedState()
        panel.render(TasksState.Stale(last, TasksState.MoonFailed(MoonFailure.Error("bad config"))))
        assertEquals(MoonTasksPanel.TREE_CARD, panel.visibleCard)
        assertFalse(panel.loadingPanel.isLoading)
        assertEquals(9, panel.tree.model.getChildCount(panel.tree.model.root))
        assertTrue(panel.tree.isExpanded(panel.projectPath("web")))
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
            TasksState.Loading(TestData.recordedState()),
            TasksState.NoWorkspace,
            TasksState.MoonFailed(MoonFailure.TimedOut),
        )
        states.forEach(panel::render)
        assertEquals(states.size, updates)
    }
}
