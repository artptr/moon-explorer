package io.github.artptr.moonexplorer.model

/**
 * The alphabetical order of IDs and names in the plugin. Upper case and lower case are equal.
 * If two values are equal without case, the order with case decides. Thus the order is always the same.
 */
val ID_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER.thenComparing(naturalOrder())

/** A task of a moon project. [target] has the format `<project>:<task>`. */
data class MoonTask(val id: String, val target: String, val description: String?)

/** A moon project and its tasks, sorted by ID. [stack] is null if the project has no `stack` value. */
data class MoonProject(val id: String, val tasks: List<MoonTask>, val stack: String? = null)

/** All projects of a workspace that have tasks, sorted by ID. */
data class WorkspaceTasks(val projects: List<MoonProject>) {

    /**
     * Gives a copy with the `stack` value of each project from [stacks]. The keys of [stacks] are project IDs.
     * If [stacks] does not contain a project, the project gets no `stack` value. The tree then shows it in the node "(none)".
     */
    fun withStacks(stacks: Map<String, String?>): WorkspaceTasks =
        WorkspaceTasks(projects.map { it.copy(stack = stacks[it.id]) })
}

/** How the tree of the Moon tool window puts the tasks into groups. */
enum class TaskGrouping {
    /** Each project node contains its tasks. */
    PROJECT,

    /** Each task name node contains the tasks with that name. The tree shows the project of each task. */
    TASK_NAME,

    /** Each `stack` value node contains its projects. Each project node contains its tasks. */
    STACK,
}
