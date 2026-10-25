package io.github.artptr.moonexplorer.model

/**
 * The alphabetical order of IDs and names in the plugin. Upper case and lower case are equal.
 * If two values are equal without case, the order with case decides. Thus the order is always the same.
 */
val ID_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER.thenComparing(naturalOrder())

/** A task of a moon project. [target] has the format `<project>:<task>`. */
data class MoonTask(val id: String, val target: String, val description: String?)

/** Gives the project ID of [target], the part before the last `:`. */
fun targetProjectId(target: String): String = target.substringBeforeLast(':')

/** Gives the task ID of [target], the part after the last `:`. */
fun targetTaskId(target: String): String = target.substringAfterLast(':')

/**
 * A moon project and its tasks, sorted by ID.
 * [stack] is null if the project has no `stack` value. [layer] is null if the project has no `layer` value.
 */
data class MoonProject(val id: String, val tasks: List<MoonTask>, val stack: String? = null, val layer: String? = null)

/**
 * The location of a moon project. [source] is the path from the workspace root to the project directory.
 * The path uses `/` as the separator, for example `packages/ui`.
 */
data class ProjectSource(val id: String, val source: String)

/**
 * All projects of a workspace that have tasks, sorted by ID.
 * [sources] contains the location of each project, sorted by ID. This list also contains the projects without tasks.
 */
data class WorkspaceTasks(val projects: List<MoonProject>, val sources: List<ProjectSource> = emptyList())

/** All tasks of all projects, in the sequence of [WorkspaceTasks.projects]. */
val WorkspaceTasks.allTasks: List<MoonTask> get() = projects.flatMap { it.tasks }

/** The main group of the tree of the Moon tool window. It sets the structure in each [TaskCategory] node. */
enum class TaskGrouping {
    /** Each project node contains its tasks. */
    PROJECT,

    /** Each task name node contains the tasks with that name. The tree shows the project of each task. */
    TASK_NAME,
}

/**
 * The category of the tree of the Moon tool window. If the category is not [NONE], the tree has a node for each value at the top.
 * Each value node contains the [TaskGrouping] nodes of its projects.
 */
enum class TaskCategory {
    /** The tree has no category nodes. */
    NONE,

    /** A node for each `stack` value. */
    STACK,

    /** A node for each `layer` value. */
    LAYER,
    ;

    /** Gives the value of [project] for this category, or null if the project has no value. [NONE] has no values. The tree has no category nodes for it. */
    fun valueFor(project: MoonProject): String? = when (this) {
        NONE -> null
        STACK -> project.stack
        LAYER -> project.layer
    }
}
