package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.OutputProblem
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.findProjectForFile
import io.github.artptr.moonexplorer.model.sortedById
import io.github.artptr.moonexplorer.model.targetProjectId
import io.github.artptr.moonexplorer.run.MoonTaskRunner
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.settings.MoonSettingsConfigurable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** The content of the "Moon" tool window: a toolbar, and a tree of tasks or a message. */
class MoonTasksPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    internal val tree = Tree(DefaultTreeModel(null)).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = TaskTreeCellRenderer()
    }

    /**
     * The speed search of the tree. The user selects the tree and types text. The search also examines the nodes in collapsed nodes,
     * and expands the parent nodes of the node that it finds.
     */
    internal val speedSearch: TreeSpeedSearch = TreeSpeedSearch.installOn(tree, true) { searchText(it.lastPathComponent) }

    internal val messageArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(8)
    }

    /** Opens the settings page. The panel shows this link only if the user can correct the failure in the settings. Refer to [hasSettingsLink]. */
    internal val settingsLink = ActionLink(MoonExplorerBundle.message("tasks.link.openSettings")) {
        MoonSettingsConfigurable.show(project)
    }.apply {
        border = JBUI.Borders.empty(0, 8, 8, 8)
        isVisible = false
    }

    private val messagePanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(messageArea), BorderLayout.CENTER)
        add(settingsLink, BorderLayout.SOUTH)
    }

    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply {
        add(JBScrollPane(tree), TREE_CARD)
        add(messagePanel, MESSAGE_CARD)
    }

    internal val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply {
        setLoadingText(MoonExplorerBundle.message("tasks.loading"))
        add(cards, BorderLayout.CENTER)
    }

    /** The name of the card that the panel shows now. */
    internal val visibleCard: String get() = if (messagePanel.isVisible) MESSAGE_CARD else TREE_CARD

    private val job: Job

    private val actionToolbar: ActionToolbar

    private val settings = project.service<MoonSettings>()

    /**
     * Runs a task. The default value starts a run configuration. Tests change this value to a fake.
     * Change it only on the EDT.
     */
    internal var runTask: (MoonTask) -> Unit = { MoonTaskRunner.run(project, it.target) }

    /**
     * Shows an information message to the user. [text] is plain text, not HTML. The default value shows a balloon below the toolbar.
     * Tests change this value to a fake. Change it only on the EDT.
     */
    internal var showInfo: (String) -> Unit = { text ->
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(plainTextHtml(text), MessageType.INFO, null)
            .setFadeoutTime(INFO_FADEOUT_MILLIS)
            .createBalloon()
            .show(RelativePoint.getSouthWestOf(toolbar ?: this), Balloon.Position.below)
    }

    /**
     * Updates the actions of the toolbar. The default value starts an update of the toolbar. Tests change this value to a fake.
     * Change it only on the EDT.
     */
    internal var updateToolbar: () -> Unit = { actionToolbar.updateActionsAsync() }

    /** The loaded state that the tree shows now. The value is null if the panel shows a message. */
    private var shown: TasksState.Loaded? = null

    init {
        val group = ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID) as? ActionGroup
            ?: error("plugin.xml must register the action group $TOOLBAR_GROUP_ID")
        actionToolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        actionToolbar.targetComponent = this
        setToolbar(actionToolbar.component)
        setContent(loadingPanel)
        installTaskActions()

        val service = project.service<MoonTasksService>()
        job = service.scope.launch(Dispatchers.EDT) {
            service.state.collect { render(it) }
        }
    }

    /** Adds the context menu, the double-click, and the Enter key to the task nodes. */
    private fun installTaskActions() {
        PopupHandler.installPopupMenu(tree, TASK_POPUP_GROUP_ID, TASK_POPUP_PLACE)
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = onDoubleClick(tree.getPathForLocation(event.x, event.y))
        }.installOn(tree)
        RunOnEnterAction().registerCustomShortcutSet(CommonShortcuts.ENTER, tree, this)
    }

    /**
     * Runs the selected task when the user pushes Enter in the tree. Then the action closes the speed search.
     * The action has the text of the action [RUN_TASK_ACTION_ID]. It is disabled if the selection is not one task node.
     * Then the tree gets the Enter key.
     */
    internal inner class RunOnEnterAction : DumbAwareAction() {
        init {
            ActionUtil.copyFrom(this, RUN_TASK_ACTION_ID)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedTask() != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val task = selectedTask() ?: return
            runTask(task)
            speedSearch.hidePopup()
        }
    }

    /**
     * Runs the task of [path] after a double-click. Gives true if the node is a task node.
     * A double-click on a project node expands or collapses the node. The tree does this, thus the function gives false.
     */
    internal fun onDoubleClick(path: TreePath?): Boolean {
        val node = path?.lastPathComponent as? TaskNode ?: return false
        runTask(node.task)
        return true
    }

    /**
     * Gives the items of the selected nodes. [item] gives the item of a node, or null if the node is not of the correct type.
     * The value is null if there is no selection, or if [item] gives null for a selected node.
     * The tree can show an item in more than one category node. The list contains each item one time.
     */
    private inline fun <T> selectedItems(item: (Any?) -> T?): List<T>? =
        tree.selectionPaths?.map { item(it.lastPathComponent) ?: return null }?.distinct()

    private fun selectedTaskNodeTasks(): List<MoonTask>? = selectedItems { (it as? TaskNode)?.task }

    /** Gives the selected task. The value is null if the selection is not one task. Two nodes of the same task are one task. */
    internal fun selectedTask(): MoonTask? = selectedTaskNodeTasks()?.singleOrNull()

    /** Gives the selected project. The value is null if the selection is not one project. Two nodes of the same project are one project. */
    internal fun selectedProject(): MoonProject? = selectedItems { (it as? ProjectNode)?.project }?.singleOrNull()

    /**
     * Gives the selected tasks. The value is null if the selection has less than two tasks, or if a selected node is not a task node.
     * The list contains each task one time.
     */
    internal fun selectedTasks(): List<MoonTask>? = selectedTaskNodeTasks()?.takeIf { it.size > 1 }

    /**
     * Gives the tasks in the selected task name nodes, sorted by project ID. All selected nodes must have the same task name.
     * The tree can show a task name in more than one category node. The list contains the tasks of all these nodes, each task one time.
     * The value is null if a selected node is not a task name node, or if the nodes have different names.
     * The value is also null if the nodes have no task nodes.
     */
    internal fun selectedTaskNameTasks(): List<MoonTask>? {
        val nodes = selectedItems { it as? TaskNameNode }?.takeIf { nodes -> nodes.distinctBy { it.name }.size == 1 } ?: return null
        return nodes.flatMap(::childTasks).distinct().sortedById { targetProjectId(it.target) }.takeIf { it.isNotEmpty() }
    }

    private fun childTasks(node: TaskNameNode): List<MoonTask> = node.children().toList().filterIsInstance<TaskNode>().map { it.task }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[SELECTED_TASK] = selectedTask()
        sink[SELECTED_TASKS] = selectedTasks()
        sink[SELECTED_TASK_NAME_TASKS] = selectedTaskNameTasks()
        sink[SELECTED_PROJECT] = selectedProject()
        sink[SHOWN_TASKS] = shown
        sink[TASKS_PANEL] = this
    }

    /**
     * Shows [state]. For [TasksState.Stale], the panel shows the last correct tasks. [RefreshFailureNotifier] shows the error.
     * Call this function on the EDT.
     */
    internal fun render(state: TasksState) {
        // The action ShowRefreshFailureAction is visible only in the Stale state. This call updates the toolbar for all states, also for the Loading state.
        updateToolbar()
        if (state is TasksState.Loading) {
            loadingPanel.startLoading()
            return
        }
        // Stop the loading indicator first. Thus an error in the code below does not keep the indicator visible.
        loadingPanel.stopLoading()
        when (state) {
            is TasksState.Loaded -> showLoaded(state)
            is TasksState.Stale -> showLoaded(state.last)
            TasksState.NoWorkspace -> showMessage(MoonExplorerBundle.message("tasks.message.noWorkspace"))
            is TasksState.Failure -> showMessage(failureText(state), showSettingsLink = hasSettingsLink(state))
            TasksState.Loading -> Unit
        }
    }

    private fun showLoaded(state: TasksState.Loaded) {
        if (state.tasks.projects.isEmpty()) {
            showMessage(MoonExplorerBundle.message("tasks.message.noTasks"))
            return
        }
        // A failed refresh and a refresh without changes give the tasks that the tree shows now. Then the tree stays the same.
        val sameTasks = state.tasks == shown?.tasks
        shown = state
        if (sameTasks) cardLayout.show(cards, TREE_CARD) else showTree(state.tasks)
    }

    /** The main group of the tree. The plugin keeps the value in the settings of the IDE project. */
    internal val mainGroup: TaskGrouping get() = settings.mainGroup

    /** The category nodes at the top of the tree. The plugin keeps the value in the settings of the IDE project. */
    internal val category: TaskCategory get() = settings.taskCategory

    /** Saves [value] and shows the tree again with this main group. Call this function on the EDT. */
    internal fun changeMainGroup(value: TaskGrouping) {
        if (value == mainGroup) return
        settings.mainGroup = value
        showTreeAgain()
    }

    /** Saves [value] and shows the tree again with this category. Call this function on the EDT. */
    internal fun changeCategory(value: TaskCategory) {
        if (value == category) return
        settings.taskCategory = value
        showTreeAgain()
    }

    /** Shows the tree again for the current main group and category. */
    private fun showTreeAgain() {
        shown?.tasks?.let(::showTree)
    }

    private fun showTree(tasks: WorkspaceTasks) {
        // Keep the expanded nodes and the selected nodes after a refresh. The keys of the nodes contain IDs,
        // thus a change of a description does not collapse a node.
        val expanded = TreeUtil.collectExpandedObjects(tree, ::keyPath).toSet()
        val selectedKeys = tree.selectionPaths.orEmpty().map(::keyPath).toSet()

        (tree.model as DefaultTreeModel).setRoot(buildTaskTree(tasks, category, mainGroup))

        // The tree can have many nodes, for example with the category Tag. Thus examine only the nodes on the way to the old
        // expanded nodes and the old selected nodes. The key paths of the expanded nodes already contain the key paths of their parent nodes.
        val found = findPaths(expanded + selectedKeys.flatMap { keys -> keys.indices.map { keys.take(it + 1) } })
        found.filterKeys { it in expanded }.values.forEach(tree::expandPath)
        val kept = found.filterKeys { it in selectedKeys }
        // After a change of the category, the main group, or the tags of a project, some selected paths are not in the new tree.
        // Select the same tasks and projects in the new tree. A task name node below a category node contains only the tasks
        // of the category. Thus the function does not select a task name node again.
        val missing = selectedKeys.mapNotNull { it.lastOrNull() }.toSet() - kept.keys.map { it.last() }.toSet()
        val moved = mutableListOf<TreePath>()
        if (missing.isNotEmpty()) {
            // The keys of the old top nodes of each selected item. After a change of the main group, the old category node can contain the item.
            val oldTopKeys = selectedKeys.filter { it.size > 1 }.groupBy({ it.last() }, { it.first() })
            // The keys of the category nodes that contain a selected item.
            val selectedCategories = kept.values.mapNotNullTo(HashSet(), ::categoryKey)
            val candidates = pathsOf { it.key in missing && it !is TaskNameNode }.groupBy { (it.lastPathComponent as TaskTreeNode).key }
            // The first node of a project is before the first node of its tasks. Thus a task goes to the category node of its project.
            for ((key, paths) in candidates) {
                val topKeys = oldTopKeys[key].orEmpty()
                val path = preferredPath(paths, { categoryKey(it) in topKeys }, { categoryKey(it) in selectedCategories }) ?: continue
                moved += path
                categoryKey(path)?.let(selectedCategories::add)
            }
        }
        if (kept.isEmpty()) moved.firstOrNull()?.let(tree::scrollPathToVisible)
        tree.selectionPaths = (kept.values + moved).toTypedArray()
        cardLayout.show(cards, TREE_CARD)
    }

    /**
     * Gives the paths of the nodes that have a key path in [keyPaths], with the key path of each node. The sequence is the sequence of the tree.
     * The function examines the child nodes of a node only if [keyPaths] contains the key path of the node. Thus it does not examine all nodes.
     */
    private fun findPaths(keyPaths: Set<List<String>>): Map<List<String>, TreePath> {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return emptyMap()
        val result = LinkedHashMap<List<String>, TreePath>()
        fun visit(path: TreePath, keys: List<String>) {
            for (child in (path.lastPathComponent as DefaultMutableTreeNode).children()) {
                val childKeys = keys + (child as TaskTreeNode).key
                if (childKeys !in keyPaths) continue
                val childPath = path.pathByAddingChild(child)
                result[childKeys] = childPath
                visit(childPath, childKeys)
            }
        }
        visit(TreePath(root), emptyList())
        return result
    }

    /** Gives the paths of the nodes for which [match] gives true, in the sequence of the tree. The function examines all nodes. */
    internal fun pathsOf(match: (TaskTreeNode) -> Boolean): List<TreePath> {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return emptyList()
        return root.preorderEnumeration().asSequence().filterIsInstance<TaskTreeNode>().filter(match).map { TreePath(it.path) }.toList()
    }

    /**
     * Gives the path in [paths] that the tree selects, for example for a task or a project in more than one category node.
     * The rules are, in this sequence: the rules in [preferred], a node on the screen, a node with expanded parent nodes.
     * The function gives the first path that obeys the first applicable rule. If no path obeys a rule, the function gives the first path.
     */
    private fun preferredPath(paths: List<TreePath>, vararg preferred: (TreePath) -> Boolean): TreePath? {
        for (rule in listOf<(TreePath) -> Boolean>(*preferred, ::isOnScreen, tree::isVisible)) {
            paths.firstOrNull(rule)?.let { return it }
        }
        return paths.firstOrNull()
    }

    /** Gives true if the node of [path] is in the visible area of the tree. If the tree has no size, the function gives false. */
    private fun isOnScreen(path: TreePath): Boolean = tree.isVisible(path) && tree.getPathBounds(path)?.intersects(tree.visibleRect) == true

    /** Gives the key of the category node of [path], or null if the tree has no category nodes. */
    private fun categoryKey(path: TreePath): String? = (path.path.getOrNull(1) as? CategoryNode)?.key

    private fun keyPath(path: TreePath): List<String> = path.path.drop(1).map { (it as TaskTreeNode).key }

    /**
     * Gives true if the panel can select the project of a file. The tree must show the tasks, and the main group must be
     * [TaskGrouping.PROJECT]. If the main group is [TaskGrouping.TASK_NAME], the tree has no project nodes.
     * During a load, the tree shows the old tasks. Thus the function gives false.
     */
    internal fun canSelectProject(): Boolean = shown != null && !loadingPanel.isLoading && mainGroup == TaskGrouping.PROJECT

    /**
     * Selects the node of the project that contains [file]. The function expands the node and its parent nodes, and scrolls to the node.
     * If the tree shows the project in more than one category node, the function selects one of these nodes.
     * The function uses these rules first: a project node that contains the selection, then a category node that contains the selection.
     * Then it uses the other rules of [preferredPath].
     * If no project contains the file, or if the project has no tasks, the function shows a message and does not change the selection.
     * Call this function on the EDT.
     */
    internal fun selectProjectOf(file: Path) {
        val state = shown?.takeIf { canSelectProject() } ?: return
        val source = findProjectForFile(state.root, file, state.tasks.sources)
        if (source == null) {
            showInfo(MoonExplorerBundle.message("tasks.selectOpenedFile.notInProject", file.fileName?.toString() ?: file.toString()))
            return
        }
        val selected = tree.selectionPaths.orEmpty()
        val selectedCategories = selected.mapNotNullTo(HashSet(), ::categoryKey)
        val path = preferredPath(
            pathsOf { it is ProjectNode && it.project.id == source.id },
            { path -> selected.any(path::isDescendant) },
            { categoryKey(it) in selectedCategories },
        )
        if (path == null) {
            // The tree shows only the projects with tasks.
            showInfo(MoonExplorerBundle.message("tasks.selectOpenedFile.noTasks", source.id))
            return
        }
        // TreeUtil.selectPath expands the parent nodes, but not the project node. Expand it to show the tasks.
        tree.expandPath(path)
        TreeUtil.selectPath(tree, path)
    }

    private fun showMessage(text: String, showSettingsLink: Boolean = false) {
        shown = null
        messageArea.text = text
        settingsLink.isVisible = showSettingsLink
        messageArea.caretPosition = 0
        cardLayout.show(cards, MESSAGE_CARD)
    }

    override fun dispose() {
        job.cancel()
    }

    internal companion object {
        const val TREE_CARD = "tree"
        const val MESSAGE_CARD = "message"
        const val TOOLBAR_GROUP_ID = "MoonExplorer.TasksToolbar"
        const val TOOLBAR_PLACE = "MoonExplorer.TasksToolWindow"
        const val TASK_POPUP_GROUP_ID = "MoonExplorer.TaskPopup"
        const val TASK_POPUP_PLACE = "MoonExplorer.TaskPopup"
        const val RUN_TASK_ACTION_ID = "MoonExplorer.RunTask"
        const val INFO_FADEOUT_MILLIS = 5_000L
    }
}

/** Gives the text that tells the user about [failure]. The text is plain text, not HTML. */
internal fun failureText(failure: TasksState.Failure): String = when (failure) {
    is TasksState.MoonFailed -> moonFailureText(failure.cause)
    is TasksState.InvalidOutput -> invalidOutputText(failure.problem)
    TasksState.UnexpectedError -> MoonExplorerBundle.message("tasks.message.unexpectedError")
}

private fun moonFailureText(cause: MoonFailure): String = when (cause) {
    is MoonFailure.NotFound -> {
        val locations = cause.examined.joinToString("\n") { "  $it" }
        MoonExplorerBundle.message("tasks.message.moonNotFound", locations)
    }
    is MoonFailure.Error -> MoonExplorerBundle.message("tasks.message.moonError", cause.text)
    is MoonFailure.ExitCode -> MoonExplorerBundle.message("tasks.message.moonExitCode", cause.code.toString())
    MoonFailure.Cancelled -> MoonExplorerBundle.message("tasks.message.moonCancelled")
    is MoonFailure.StartFailed -> detailText(cause.detail, "tasks.message.moonStartFailed", "tasks.message.moonStartFailedNoDetail")
    MoonFailure.TimedOut -> MoonExplorerBundle.message("tasks.message.moonTimeout")
}

private fun invalidOutputText(problem: OutputProblem): String = when (problem) {
    is OutputProblem.NotJson -> detailText(problem.detail, "tasks.message.invalidOutput.notJson", "tasks.message.invalidOutput.notJsonNoDetail")
    OutputProblem.NoProjects -> MoonExplorerBundle.message("tasks.message.invalidOutput.noProjects")
}

/** Gives the message [key] with [detail]. If [detail] is null or blank, gives the message [noDetailKey]. */
private fun detailText(detail: String?, key: String, noDetailKey: String): String =
    StringUtil.nullize(detail, true)?.let { MoonExplorerBundle.message(key, it) } ?: MoonExplorerBundle.message(noDetailKey)

/** Gives true if the user can correct [failure] in the settings. Then the plugin shows a link to the settings. */
internal fun hasSettingsLink(failure: TasksState.Failure): Boolean =
    failure is TasksState.MoonFailed && when (failure.cause) {
        is MoonFailure.NotFound, is MoonFailure.StartFailed, MoonFailure.TimedOut -> true
        is MoonFailure.Error, is MoonFailure.ExitCode, MoonFailure.Cancelled -> false
    }

/**
 * Gives the HTML text of a balloon or a notification for the plain text [text]. Balloons and notifications read their text as HTML.
 * Thus a file name with `<`, `>`, or `&` must change into HTML entities. A line break changes into `<br>`.
 */
internal fun plainTextHtml(text: String): String = StringUtil.escapeXmlEntities(text).replace("\n", "<br>")
