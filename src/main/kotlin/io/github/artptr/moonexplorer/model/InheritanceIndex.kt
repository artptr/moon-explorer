package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/**
 * A global task that a project inherits. [globalId] is the task key in [files]. [localId] is the task ID in the project.
 * [files] contains the files in `.moon/tasks/` that define the task and that exist. The list is not empty.
 */
data class InheritedTask(val globalId: String, val localId: String, val files: List<Path>)

/**
 * The inheritance of one project. [inherited] contains the inherited tasks, in [ID_ORDER] of the global IDs.
 * [merged] gives the global tasks that merge with each local task ID. Refer to [mergeStrategies] for the merge strategies.
 */
data class ProjectInheritance(val inherited: List<InheritedTask>, val merged: Map<String, List<InheritedTask>>)

/** A project that merges a global task with its local task [localId]. Refer to [mergeStrategies] for the merge strategies. */
data class MergingProject(val projectId: String, val localId: String)

/**
 * The inheritance of a global task in one file.
 * [inheritedBy] gives the local task ID in each project that inherits the task. The projects are in [ID_ORDER].
 * [mergedIn] contains the projects with a local task that merges. The projects are in [ID_ORDER].
 * [targets] contains the targets of [inheritedBy] that the user can run, in the same sequence. Internal tasks are not in the list.
 * Refer to [dependents] for the dependents of the task.
 */
data class GlobalTaskInheritance(
    val inheritedBy: Map<String, String>,
    val mergedIn: List<MergingProject>,
    val targets: List<String>,
)

/**
 * The inheritance of one file in `.moon/tasks/`. [projects] gives the projects that inherit one or more tasks of the file, in [ID_ORDER].
 * The value of each project gives the local IDs of the inherited tasks, in [ID_ORDER].
 */
data class FileInheritance(val projects: Map<String, List<String>>, val tasks: Map<String, GlobalTaskInheritance>)

/**
 * The task inheritance and the targets of the task keys of one load. The keys with paths are absolute and normalized.
 *
 * moon finds the files of each project. The plugin does not calculate which files apply to a project.
 * It only uses `include`, `exclude`, and `rename` of each project. Refer to [TaskConfigs.inheritedLayers].
 * The index examines the tasks with `taskConfigs`, not with the task list, because the task list does not contain internal tasks.
 * The task list only gives the tasks that the user can run.
 *
 * The index does not use the task graph. Thus the plugin does not make the index again after a new task graph. Refer to [mergeStrategies] and [dependents].
 * The index does not start moon and does not read the disk.
 */
internal class InheritanceIndex private constructor(
    private val projects: Map<String, ProjectInheritance>,
    private val projectFiles: Map<Path, String>,
    private val projectTargets: Map<String, Map<String, String>>,
    private val files: Map<Path, FileInheritance>,
) {
    /** Gives the inheritance of the project [projectId], or null if the project has no inherited tasks. */
    fun project(projectId: String): ProjectInheritance? = projects[projectId]

    /** Gives the ID of the project with the configuration file [file], or null if [file] is not the configuration file of a project. */
    fun projectOfFile(file: Path): String? = projectFiles[file]

    /** Gives the inheritance of the inherited file [file], or null if no project of the load has this file in `inherited.layers`. */
    fun file(file: Path): FileInheritance? = files[file]

    /**
     * Gives the targets of the task key [taskId] in the configuration file [file]. [file] must be absolute and normalized.
     * Gives null if [file] is not a configuration file of the load, or if no project has a task with this key that the user can run.
     * The targets of a key in an inherited file are [GlobalTaskInheritance.targets]. Thus the run icon and the hints use the same index.
     * The run icon does not run the internal tasks. The plugin calls this function for each task key during the analysis of a file.
     */
    fun targets(file: Path, taskId: String): TaskKeyTargets? {
        val projectId = projectFiles[file]
        if (projectId != null) return projectTargets[projectId]?.get(taskId)?.let(TaskKeyTargets::Single)
        return files[file]?.tasks?.get(taskId)?.targets?.takeIf { it.isNotEmpty() }?.let(TaskKeyTargets::Shared)
    }

    companion object {
        /** Makes the index of [tasks] with the configuration files [configFiles] of the same load. */
        fun of(tasks: WorkspaceTasks, configFiles: ExistingConfigFiles): InheritanceIndex {
            // The target of each task that the user can run, for each project. Thus it is not necessary to scan the tasks for each task key.
            val projectTargets = tasks.projects.associate { project -> project.id to project.tasks.associate { it.id to it.target } }
            val configs = tasks.taskConfigs
            val projects = LinkedHashMap<String, ProjectInheritance>()
            for (projectId in configs.keys.sortedWith(ID_ORDER)) {
                projectInheritance(configs.getValue(projectId), configFiles.inherited)?.let { projects[projectId] = it }
            }
            // The key of a row is the path, not the layer string of moon. Thus two spellings of a layer, for example
            // `./.moon/tasks/all.yml` and `.moon/tasks/all.yml`, give the same file.
            val rows = projects.flatMap { (projectId, project) ->
                project.inherited.flatMap { task -> task.files.map { file -> FileRow(file, projectId, task, task.localId in project.merged) } }
            }.groupBy { it.file }
            // All known inherited files have an entry. Thus a file that no project inherits after the filters has the count 0.
            val files = configFiles.inherited.values.associateWith { fileInheritance(rows[it].orEmpty(), projectTargets) }
            val projectFiles = configFiles.projects.entries.associate { (id, file) -> file to id }
            return InheritanceIndex(projects, projectFiles, projectTargets, files)
        }

        /** Gives the inheritance of a project, or null if the project has no inherited tasks. */
        private fun projectInheritance(config: TaskConfigs, existing: Map<String, Path>): ProjectInheritance? {
            val inherited = inheritedTasks(config, existing)
            if (inherited.isEmpty()) return null
            val merged = inherited.filter { it.localId in config.ownTasks }.groupBy { it.localId }
            return ProjectInheritance(inherited, merged)
        }

        /** Gives the inherited tasks of a project. The function ignores the files that are not on the disk. */
        private fun inheritedTasks(config: TaskConfigs, existing: Map<String, Path>): List<InheritedTask> =
            config.inheritedLayers.flatMap { (localId, layers) ->
                layers.groupBy { it.globalId }.mapNotNull { (globalId, sameTask) ->
                    val files = sameTask.mapNotNull { existing[it.file] }.distinct()
                    if (files.isEmpty()) null else InheritedTask(globalId, localId, files)
                }
            }.sortedById { it.globalId }

        /**
         * Gives the inheritance of one file from its [rows]. The rows are in [ID_ORDER] of the projects.
         * [projectTargets] gives the targets that the user can run in each project.
         */
        private fun fileInheritance(rows: List<FileRow>, projectTargets: Map<String, Map<String, String>>) = FileInheritance(
            rows.groupBy({ it.projectId }, { it.task.localId }).mapValues { (_, ids) -> ids.distinct().sortedWith(ID_ORDER) },
            rows.groupBy { it.task.globalId }.mapValues { (_, tasks) ->
                val mergedIn = tasks.filter { it.merged }.map { MergingProject(it.projectId, it.task.localId) }
                val inheritedBy = tasks.associate { it.projectId to it.task.localId }
                // The task key in the file is the global task ID. If web renames typecheck to tsc, the key typecheck gives web:tsc.
                val targets = tasks.mapNotNull { projectTargets[it.projectId]?.get(it.task.localId) }.distinct()
                GlobalTaskInheritance(inheritedBy, mergedIn, targets)
            },
        )
    }

    /** One inherited task of a project in one file. [merged] is true if the task merges with a local task of the project. */
    private class FileRow(val file: Path, val projectId: String, val task: InheritedTask, val merged: Boolean)
}

/**
 * Gives the merge strategies of the local task [localId] of the project [projectId] that are not `append`, for example `args` to `prepend`.
 * The task graph has the values after the merge, also for internal tasks. If `moon task-graph` failed, the values come from `moon query projects`.
 * The function reads the task graph at each call. Thus the plugin does not make [TasksState.Loaded.inheritanceIndex] again after a new task graph.
 */
internal fun TasksState.Loaded.mergeStrategies(projectId: String, localId: String): Map<String, String> =
    taskGraph?.mergeStrategies?.get(taskTarget(projectId, localId)) ?: tasks.taskConfigs[projectId]?.mergeStrategies?.get(localId).orEmpty()

/**
 * Gives the direct dependents of each target of [task] that has dependents. The targets are in [ID_ORDER].
 * A dependent can be below more than one target. The map is empty if `moon task-graph` failed in the load.
 * The function reads the task graph at each call. Thus the plugin does not make [TasksState.Loaded.inheritanceIndex] again after a new task graph.
 */
internal fun TasksState.Loaded.dependents(task: GlobalTaskInheritance): Map<String, Set<String>> {
    val graph = taskGraph?.dependents ?: return emptyMap()
    return task.inheritedBy.entries.mapNotNull { (projectId, localId) ->
        val target = taskTarget(projectId, localId)
        graph[target]?.let { target to it }
    }.toMap().toSortedMap(ID_ORDER)
}

/** Gives the number of direct dependents in [dependents]. A dependent below more than one target counts one time. */
internal fun dependentCount(dependents: Map<String, Set<String>>): Int = dependents.values.flatMapTo(HashSet()) { it }.size
