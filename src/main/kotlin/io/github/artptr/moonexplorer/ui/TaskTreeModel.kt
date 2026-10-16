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
import javax.swing.Icon
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
        TaskGrouping.STACK -> tasks.projects.groupBy { it.stack }
            .toSortedMap(nullsLast(ID_ORDER))
            .forEach { (stack, projects) -> root.add(stackNode(stack, projects)) }
    }
    return root
}

private fun projectNode(project: MoonProject) = ProjectNode(project).apply {
    project.tasks.forEach { add(TaskNode(it)) }
}

private fun stackNode(stack: String?, projects: List<MoonProject>) = StackNode(stack).apply {
    projects.forEach { add(projectNode(it)) }
}

/**
 * A node of the task tree. [key] identifies the node in its sibling nodes after a refresh of the tree.
 * The renderer shows [icon] and [toString] with [attributes].
 */
sealed class TaskTreeNode(userObject: Any?, allowsChildren: Boolean = true) : DefaultMutableTreeNode(userObject, allowsChildren) {
    abstract val key: String
    abstract val icon: Icon
    open val attributes: SimpleTextAttributes get() = SimpleTextAttributes.REGULAR_ATTRIBUTES
}

/** A tree node for a project. [toString] gives the project ID. */
class ProjectNode(val project: MoonProject) : TaskTreeNode(project) {
    override val key: String = "project:${project.id}"
    override val icon: Icon get() = AllIcons.Nodes.Module
    override fun toString(): String = project.id
}

/**
 * A tree node for a task. [toString] gives [label], the name that the tree shows.
 * If [showProject] is true, the node shows the project ID, because the parent node gives the task name.
 */
class TaskNode(val task: MoonTask, val showProject: Boolean = false) : TaskTreeNode(task, false) {
    override val key: String = "task:${task.target}"
    override val icon: Icon get() = if (showProject) AllIcons.Nodes.Module else AllIcons.Nodes.Function
    override fun toString(): String = label

    /** The ID of the project of the task. */
    val projectId: String get() = task.target.substringBeforeLast(':')

    /** The name that the node shows. It is the project ID if the parent node gives the task name. If not, it is the task ID. */
    val label: String get() = if (showProject) projectId else task.id
}

/** A tree node for a task name. It contains the tasks with this name in all projects. */
class TaskNameNode(val name: String) : TaskTreeNode(name) {
    override val key: String = "name:$name"
    override val icon: Icon get() = AllIcons.Nodes.Function
    override fun toString(): String = name
}

/** A tree node for a `stack` value. [stack] is null for the node of the projects without a `stack` value. */
class StackNode(val stack: String?) : TaskTreeNode(stack) {
    override val key: String = "stack:${stack.orEmpty()}"
    override val icon: Icon get() = AllIcons.Nodes.Folder
    override val attributes: SimpleTextAttributes
        get() = if (stack == null) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
    override fun toString(): String = stack ?: MoonExplorerBundle.message("tasks.tree.noStack")
}

/** The separator between the name of a task node and its description. */
private const val DESCRIPTION_SEPARATOR = "  "

/**
 * Gives the text that the speed search of the tree examines for [node].
 * For a task node, the text contains the target and the description. The target contains the project ID and the task ID.
 * Thus the search finds `web:build`, and the search for `web` also finds the tasks of the project `web`.
 * The renderer highlights the found text in the text that the node shows. That text does not contain the full target.
 * For other nodes, the text is the text that the node shows.
 */
fun searchText(node: Any?): String = when (node) {
    is TaskNode -> node.task.target + node.task.description?.let { DESCRIPTION_SEPARATOR + it }.orEmpty()
    else -> node?.toString().orEmpty()
}

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
        if (value is TaskTreeNode) {
            icon = value.icon
            append(value.toString(), value.attributes)
            (value as? TaskNode)?.task?.description?.let { append(DESCRIPTION_SEPARATOR + it, SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        } else {
            // A node of a different type shows its text. Thus the node is not empty in the tree.
            value?.let { append(it.toString()) }
        }
        // Highlight the found text in all fragments. The search also examines the description.
        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected)
    }
}
