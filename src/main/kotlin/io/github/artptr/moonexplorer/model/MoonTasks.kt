package io.github.artptr.moonexplorer.model

/**
 * The alphabetical order of IDs and names in the plugin. Upper case and lower case are equal.
 * If two values are equal without case, the order with case decides. Thus the order is always the same.
 */
val ID_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER.thenComparing(naturalOrder())

/** Sorts the items in [ID_ORDER] of the ID that [id] gives. */
internal fun <T> List<T>.sortedById(id: (T) -> String): List<T> = sortedWith(compareBy(ID_ORDER, id))

/** A task of a moon project. [target] has the format `<project>:<task>`. */
data class MoonTask(val id: String, val target: String, val description: String?)

/** Gives the project ID of [target], the part before the last `:`. */
fun targetProjectId(target: String): String = target.substringBeforeLast(':')

/** Gives the task ID of [target], the part after the last `:`. */
fun targetTaskId(target: String): String = target.substringAfterLast(':')

/**
 * A moon project and its tasks, sorted by ID.
 * [stack] is null if the project has no `stack` value. [layer] is null if the project has no `layer` value.
 * [tags] contains the tags of the project in the sequence of the configuration. The set is empty if the project has no tags.
 */
data class MoonProject(
    val id: String,
    val tasks: List<MoonTask>,
    val stack: String? = null,
    val layer: String? = null,
    val tags: Set<String> = emptySet(),
)

/**
 * The location of a moon project. [source] is the path from the workspace root to the project directory.
 * The path uses `/` as the separator, for example `packages/ui`.
 */
data class ProjectSource(val id: String, val source: String)

/**
 * The settings in `workspace.inheritedTasks` of the configuration file of a project. The keys of the settings are global task IDs.
 * [include] contains the global tasks that the project can inherit. If [include] is null, the project can inherit all global tasks.
 * [exclude] contains the global tasks that the project does not inherit. [rename] gives the task ID in the project for a global task ID.
 */
data class InheritedTasksFilter(
    val include: Set<String>? = null,
    val exclude: Set<String> = emptySet(),
    val rename: Map<String, String> = emptyMap(),
) {
    /** Gives the task ID in the project for the global task [globalId], or null if the project does not inherit the task. */
    fun localId(globalId: String): String? {
        if (include?.contains(globalId) == false || globalId in exclude) return null
        return rename[globalId] ?: globalId
    }

    /**
     * Gives the inherited files of each task ID of the project. The keys of the result are the task IDs after [rename].
     * [layers] gives the files of each global task ID from `inherited.layers`. [taskIds] contains the IDs of all tasks of the project,
     * also the internal tasks. The project inherits a global task only if [localId] gives a task ID and [taskIds] contains this task ID.
     * The check of [taskIds] uses the result of moon. Thus a project without tasks inherits no task.
     */
    fun inheritedLayers(layers: Map<String, List<String>>, taskIds: Set<String>): Map<String, List<InheritedLayer>> {
        val result = LinkedHashMap<String, MutableList<InheritedLayer>>()
        for ((globalId, files) in layers) {
            val localId = localId(globalId)?.takeIf { it in taskIds } ?: continue
            // Two global tasks can have the same task ID if the project renames a task.
            // Then the project task inherits the files of the two global tasks.
            files.mapTo(result.getOrPut(localId, ::ArrayList)) { InheritedLayer(globalId, it) }
        }
        return result
    }
}

/**
 * An inherited file of a task of a project. [file] is a path from the workspace root, for example `.moon/tasks/frontend.yml`.
 * [globalId] is the task key in [file]. It is different from the task ID in the project if the project renames the task.
 */
data class InheritedLayer(val globalId: String, val file: String)

/**
 * The configuration files of the tasks of a project.
 * [ownTasks] contains the IDs of the tasks in the configuration file of the project, for example `moon.yml`.
 * [layers] gives the inherited files of each global task ID from `inherited.layers`. Each file is a path from the workspace root,
 * for example `.moon/tasks/all.yml`.
 * [inheritedLayers] gives the inherited files of each task ID of the project. Refer to [InheritedTasksFilter.inheritedLayers].
 *
 * **NOTE:** moon 2.6.4 writes in [layers] all the global tasks that the project can inherit. This includes the tasks that
 * `workspace.inheritedTasks` removes. Thus use [inheritedLayers] to find the inherited files of a task of the project.
 */
data class TaskConfigs(
    val ownTasks: Set<String> = emptySet(),
    val layers: Map<String, List<String>> = emptyMap(),
    val inheritedLayers: Map<String, List<InheritedLayer>> = emptyMap(),
)

/**
 * All projects of a workspace that have tasks, sorted by ID.
 * [sources] contains the location of each project, sorted by ID. This list also contains the projects without tasks.
 * [taskConfigs] gives the configuration files of the tasks of each project ID. A project without these files is not in the map.
 */
data class WorkspaceTasks(
    val projects: List<MoonProject>,
    val sources: List<ProjectSource> = emptyList(),
    val taskConfigs: Map<String, TaskConfigs> = emptyMap(),
)

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

    /** A node for each tag. A project with more than one tag is in more than one node. */
    TAG,
    ;

    /**
     * Gives the values of [project] for this category. Each value is in the collection only one time. The collection is empty if the project
     * has no value. [NONE] has no values. The tree adds the project one time below the node of each value.
     */
    fun valuesFor(project: MoonProject): Collection<String> = when (this) {
        NONE -> emptyList()
        STACK -> listOfNotNull(project.stack)
        LAYER -> listOfNotNull(project.layer)
        TAG -> project.tags
    }
}
