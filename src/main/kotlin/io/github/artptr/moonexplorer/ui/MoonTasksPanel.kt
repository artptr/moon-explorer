package io.github.artptr.moonexplorer.ui

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

/** The content of the "Moon" tool window: a toolbar, and a tree of tasks or a message. */
class MoonTasksPanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    internal val tree = Tree(DefaultTreeModel(null)).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = TaskTreeCellRenderer()
    }

    internal val messageArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.empty(8)
    }

    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply {
        add(JBScrollPane(tree), TREE_CARD)
        add(JBScrollPane(messageArea), MESSAGE_CARD)
    }

    internal val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply {
        setLoadingText(MoonExplorerBundle.message("tasks.loading"))
        add(cards, BorderLayout.CENTER)
    }

    /** The name of the card that the panel shows now. */
    internal var visibleCard: String = TREE_CARD
        private set

    private val job: Job

    init {
        val group = ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID) as ActionGroup
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
        setContent(loadingPanel)

        val service = project.service<MoonTasksService>()
        job = service.scope.launch(Dispatchers.EDT) {
            service.state.collect { render(it) }
        }
    }

    /** Shows [state]. Call this function on the EDT. */
    internal fun render(state: TasksState) {
        if (state is TasksState.Loading) {
            loadingPanel.startLoading()
            return
        }
        loadingPanel.stopLoading()
        when (state) {
            is TasksState.Loaded -> {
                if (state.tasks.projects.isEmpty()) {
                    showMessage(MoonExplorerBundle.message("tasks.message.noTasks"))
                } else {
                    showTree(state)
                }
            }
            TasksState.NoWorkspace -> showMessage(MoonExplorerBundle.message("tasks.message.noWorkspace"))
            TasksState.MoonNotFound -> showMessage(MoonExplorerBundle.message("tasks.message.moonNotFound"))
            is TasksState.MoonError -> showMessage(MoonExplorerBundle.message("tasks.message.moonError", state.text))
            is TasksState.InvalidOutput -> showMessage(MoonExplorerBundle.message("tasks.message.invalidOutput", state.text))
            TasksState.Loading -> Unit
        }
    }

    private fun showTree(state: TasksState.Loaded) {
        // Keep the expanded nodes and the selected nodes after a refresh.
        val treeState = TreeState.createOn(tree)
        (tree.model as DefaultTreeModel).setRoot(buildTaskTree(state.tasks))
        treeState.applyTo(tree)
        showCard(TREE_CARD)
    }

    private fun showMessage(text: String) {
        messageArea.text = text
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
    }
}
