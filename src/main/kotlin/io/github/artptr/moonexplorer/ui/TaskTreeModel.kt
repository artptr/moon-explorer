package io.github.artptr.moonexplorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.ID_ORDER
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Makes the tree nodes for [tasks] with the structure of [grouping]. All nodes are in alphabetical order.
 * - [TaskGrouping.PROJECT]: a node for each project. Each project node has a node for each task.
 * - [TaskGrouping.TASK_NAME]: a node for each task name. Each task name node has a node for each task with that name.
 * - [TaskGrouping.STACK]: a node for each `stack` value. Each stack node has its project nodes.
 *   The projects without a `stack` value are in the last node, "(none)".
 */
fun buildTaskTree(tasks: WorkspaceTasks, grouping: TaskGrouping = TaskGrouping.PROJECT): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode()
    when (grouping) {
        TaskGrouping.PROJECT -> tasks.projects.forEach { root.add(projectNode(it)) }
        TaskGrouping.TASK_NAME -> {
            tasks.projects.flatMap { it.tasks }
                .groupBy { it.id }
                .toSortedMap(ID_ORDER)
                .forEach { (name, tasksWithName) ->
                    val nameNode = TaskNameNode(name)
                    tasksWithName.forEach { nameNode.add(TaskNode(it, showProject = true)) }
                    root.add(nameNode)
                }
        }
        TaskGrouping.STACK -> {
            val (withStack, withoutStack) = tasks.projects.partition { it.stack != null }
            withStack.groupBy { it.stack!! }
                .toSortedMap(ID_ORDER)
                .forEach { (stack, projects) -> root.add(stackNode(stack, projects)) }
            if (withoutStack.isNotEmpty()) root.add(stackNode(null, withoutStack))
        }
    }
    return root
}

private fun projectNode(project: MoonProject) = ProjectNode(project).apply {
    project.tasks.forEach { add(TaskNode(it)) }
}

private fun stackNode(stack: String?, projects: List<MoonProject>) = StackNode(stack).apply {
    projects.forEach { add(projectNode(it)) }
}

/** A node of the task tree. [key] identifies the node in its sibling nodes after a refresh of the tree. */
sealed class TaskTreeNode(userObject: Any?, allowsChildren: Boolean = true) : DefaultMutableTreeNode(userObject, allowsChildren) {
    abstract val key: String
}

/** A tree node for a project. [toString] gives the project ID. */
class ProjectNode(val project: MoonProject) : TaskTreeNode(project) {
    override val key: String get() = "project:${project.id}"
    override fun toString(): String = project.id
}

/**
 * A tree node for a task. [toString] gives the task target.
 * If [showProject] is true, the node shows the project ID, because the parent node gives the task name.
 */
class TaskNode(val task: MoonTask, val showProject: Boolean = false) : TaskTreeNode(task, false) {
    override val key: String get() = "task:${task.target}"
    override fun toString(): String = task.target

    /** The ID of the project of the task. */
    val projectId: String get() = task.target.substringBeforeLast(':')
}

/** A tree node for a task name. It contains the tasks with this name in all projects. */
class TaskNameNode(val name: String) : TaskTreeNode(name) {
    override val key: String get() = "name:$name"
    override fun toString(): String = name
}

/** A tree node for a `stack` value. [stack] is null for the node of the projects without a `stack` value. */
class StackNode(val stack: String?) : TaskTreeNode(stack) {
    override val key: String get() = "stack:${stack.orEmpty()}"
    override fun toString(): String = stack ?: MoonExplorerBundle.message("tasks.tree.noStack")
}

/** The separator between the name of a task node and its description. */
private const val DESCRIPTION_SEPARATOR = "  "

/**
 * Gives the text that the tree shows for [node]. The speed search of the tree examines this text.
 * For a task node, the text contains the task ID (or the project ID) and the description.
 */
fun searchText(node: Any?): String = when (node) {
    is TaskNode -> node.label + node.task.description?.let { DESCRIPTION_SEPARATOR + it }.orEmpty()
    is TaskTreeNode -> node.toString()
    else -> ""
}

/** The name that a task node shows. It is the project ID if the parent node gives the task name. If not, it is the task ID. */
private val TaskNode.label: String get() = if (showProject) projectId else task.id

/**
 * Shows the nodes of the task tree. A task node shows its description in gray text.
 * The renderer highlights the text that the speed search found.
 */
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
        when (value) {
            is ProjectNode -> {
                icon = AllIcons.Nodes.Module
                append(value.project.id)
            }
            is TaskNode -> {
                icon = if (value.showProject) AllIcons.Nodes.Module else AllIcons.Nodes.Function
                append(value.label)
                value.task.description?.let { append(DESCRIPTION_SEPARATOR + it, SimpleTextAttributes.GRAYED_ATTRIBUTES) }
            }
            is TaskNameNode -> {
                icon = AllIcons.Nodes.Function
                append(value.name)
            }
            is StackNode -> {
                icon = AllIcons.Nodes.Folder
                append(value.toString(), if (value.stack == null) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            // A node type without a branch above shows its text. Thus a new node type is not empty in the tree.
            else -> value?.let { append(it.toString()) }
        }
        // Highlight the found text in all fragments. The search also examines the description.
        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected)
    }
}
