package io.github.artptr.moonexplorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** Makes the tree nodes for [tasks]. The root node has a node for each project. Each project node has a node for each task. */
fun buildTaskTree(tasks: WorkspaceTasks): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode()
    for (project in tasks.projects) {
        val projectNode = ProjectNode(project)
        project.tasks.forEach { projectNode.add(TaskNode(it)) }
        root.add(projectNode)
    }
    return root
}

/** A tree node for a project. [toString] gives the project ID. */
class ProjectNode(val project: MoonProject) : DefaultMutableTreeNode(project) {
    override fun toString(): String = project.id
}

/** A tree node for a task. [toString] gives the task target. */
class TaskNode(val task: MoonTask) : DefaultMutableTreeNode(task, false) {
    override fun toString(): String = task.target
}

/** Shows a project node with its ID, and a task node with its ID and its description in gray text. */
class TaskTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        when (val item = (value as? DefaultMutableTreeNode)?.userObject) {
            is MoonProject -> {
                icon = AllIcons.Nodes.Module
                append(item.id)
            }
            is MoonTask -> {
                icon = AllIcons.Nodes.Function
                append(item.id)
                item.description?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
            }
        }
    }
}
