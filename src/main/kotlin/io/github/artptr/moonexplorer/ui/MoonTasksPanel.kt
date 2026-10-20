package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.run.MoonTaskRunner
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.settings.MoonSettingsConfigurable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseEvent
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

    /** Opens the settings page. The panel shows this link only if the plugin cannot find moon. */
    internal val settingsLink = ActionLink(MoonExplorerBundle.message("tasks.link.openSettings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MoonSettingsConfigurable::class.java)
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
    internal var visibleCard: String = TREE_CARD
        private set

    private val job: Job

    private val settings = project.service<MoonSettings>()

    /**
     * Runs a task. The default value starts a run configuration. Tests change this value to a fake.
     * Change it only on the EDT.
     */
    internal var runTask: (MoonTask) -> Unit = { MoonTaskRunner.run(project, it.target) }

    /** The tasks that the tree shows now. The value is null if the panel shows a message. */
    private var shownTasks: WorkspaceTasks? = null

    init {
        val group = ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID) as? ActionGroup
            ?: error("plugin.xml must register the action group $TOOLBAR_GROUP_ID")
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
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

    /** Gives the selected task. The value is null if the selection is not one task node. */
    internal fun selectedTask(): MoonTask? {
        val paths = tree.selectionPaths ?: return null
        return (paths.singleOrNull()?.lastPathComponent as? TaskNode)?.task
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[SELECTED_TASK] = selectedTask()
        sink[TASKS_PANEL] = this
    }

    /** Shows [state]. Call this function on the EDT. */
    internal fun render(state: TasksState) {
        if (state is TasksState.Loading) {
            loadingPanel.startLoading()
            return
        }
        // Stop the loading indicator first. Thus an error in the code below does not keep the indicator visible.
        loadingPanel.stopLoading()
        when (state) {
            is TasksState.Loaded -> {
                if (state.tasks.projects.isEmpty()) {
                    showMessage(MoonExplorerBundle.message("tasks.message.noTasks"))
                } else {
                    showTree(state.tasks)
                }
            }
            TasksState.NoWorkspace -> showMessage(MoonExplorerBundle.message("tasks.message.noWorkspace"))
            is TasksState.MoonNotFound -> {
                val locations = state.examined.joinToString("\n") { "  $it" }
                showMessage(MoonExplorerBundle.message("tasks.message.moonNotFound", locations), showSettingsLink = true)
            }
            is TasksState.MoonError -> showMessage(MoonExplorerBundle.message("tasks.message.moonError", state.text))
            TasksState.MoonTimeout -> showMessage(MoonExplorerBundle.message("tasks.message.moonTimeout"), showSettingsLink = true)
            is TasksState.InvalidOutput -> showMessage(MoonExplorerBundle.message("tasks.message.invalidOutput", state.text))
            TasksState.Loading -> Unit
        }
    }

    /** The main group of the tree. The plugin keeps the value in the settings of the IDE project. */
    internal val grouping: TaskGrouping get() = settings.taskGrouping

    /** The category nodes at the top of the tree. The plugin keeps the value in the settings of the IDE project. */
    internal val category: TaskCategory get() = settings.taskCategory

    /** Saves [value] and shows the tree again with this main group. Call this function on the EDT. */
    internal fun changeGrouping(value: TaskGrouping) {
        if (value == grouping) return
        settings.taskGrouping = value
        shownTasks?.let(::showTree)
    }

    /** Saves [value] and shows the tree again with this category. Call this function on the EDT. */
    internal fun changeCategory(value: TaskCategory) {
        if (value == category) return
        settings.taskCategory = value
        shownTasks?.let(::showTree)
    }

    private fun showTree(tasks: WorkspaceTasks) {
        // Keep the expanded nodes and the selected nodes after a refresh. The keys of the nodes contain IDs,
        // thus a change of a description does not collapse a node.
        val oldPaths = allPaths()
        val expanded = oldPaths.filter(tree::isExpanded).map(::keyPath).toSet()
        val selectedPaths = tree.selectionPaths.orEmpty()
        val selectedKeys = selectedPaths.map(::keyPath).toSet()
        val selectedTargets = selectedPaths.mapNotNull { (it.lastPathComponent as? TaskNode)?.task?.target }.toSet()

        shownTasks = tasks
        (tree.model as DefaultTreeModel).setRoot(buildTaskTree(tasks, category, grouping))

        val newPaths = allPaths()
        newPaths.filter { keyPath(it) in expanded }.forEach(tree::expandPath)
        var selection = newPaths.filter { keyPath(it) in selectedKeys }
        if (selection.isEmpty() && selectedTargets.isNotEmpty()) {
            // After a change of the category or the main group, the paths are different. Select the same tasks in the new tree.
            selection = newPaths.filter { (it.lastPathComponent as? TaskNode)?.task?.target in selectedTargets }
            selection.firstOrNull()?.let(tree::scrollPathToVisible)
        }
        tree.selectionPaths = selection.toTypedArray()
        showCard(TREE_CARD)
    }

    /** Gives the paths of all nodes below the root, also the nodes in collapsed nodes. Each parent node is before its child nodes. */
    internal fun allPaths(): List<TreePath> {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return emptyList()
        return root.preorderEnumeration().toList().drop(1).map { TreePath((it as DefaultMutableTreeNode).path) }
    }

    private fun keyPath(path: TreePath): List<String> = path.path.drop(1).map { (it as TaskTreeNode).key }

    private fun showMessage(text: String, showSettingsLink: Boolean = false) {
        shownTasks = null
        messageArea.text = text
        settingsLink.isVisible = showSettingsLink
        messageArea.caretPosition = 0
        showCard(MESSAGE_CARD)
    }

    private fun showCard(name: String) {
        visibleCard = name
        cardLayout.show(cards, name)
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
    }
}
