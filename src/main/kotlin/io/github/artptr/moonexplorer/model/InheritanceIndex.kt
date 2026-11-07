package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/**
 * A global task that a project inherits. [globalId] is the task key in [files]. [localId] is the task ID in the project.
 * [files] contains the files in `.moon/tasks/` that define the task and that exist. The list is not empty.
 */
data class InheritedTask(val globalId: String, val localId: String, val files: List<Path>)

/**
 * A local task that merges with one or more global tasks. The key of [ProjectInheritance.merged] gives the local task ID.
 * [strategies] gives the merge strategies that are not `append`, for example `args` to `prepend`.
 */
data class MergedTask(val globalTasks: List<InheritedTask>, val strategies: Map<String, String>)

/**
 * The inheritance of one project. [inherited] contains the inherited tasks, in [ID_ORDER] of the global IDs.
 * [merged] gives the merged task of each local task ID.
 */
data class ProjectInheritance(val inherited: List<InheritedTask>, val merged: Map<String, MergedTask>)

/** A project that merges a global task with its local task [localId]. Refer to [MergedTask.strategies] for [strategies]. */
data class MergingProject(val projectId: String, val localId: String, val strategies: Map<String, String>)

/**
 * The inheritance of a global task in one file.
 * [inheritedBy] gives the local task ID in each project that inherits the task. The projects are in [ID_ORDER].
 * [mergedIn] contains the projects with a local task that merges. The projects are in [ID_ORDER].
 * [dependents] gives the direct dependents of each inherited target that has dependents. The targets are in [ID_ORDER].
 * A dependent can be below more than one target. The map is empty if `moon task-graph` failed in the load.
 */
data class GlobalTaskInheritance(
    val inheritedBy: Map<String, String>,
    val mergedIn: List<MergingProject>,
    val dependents: Map<String, Set<String>>,
)

/** The inheritance of one file in `.moon/tasks/`. [projects] contains the projects that inherit one or more tasks of the file, in [ID_ORDER]. */
data class FileInheritance(val projects: List<String>, val tasks: Map<String, GlobalTaskInheritance>)

/**
 * The task inheritance and the dependents of one [TasksState.Loaded]. The keys with paths are absolute and normalized.
 *
 * moon finds the files of each project. The plugin does not calculate which files apply to a project.
 * It only uses `include`, `exclude`, and `rename` of each project. Refer to [TaskConfigs.inheritedLayers].
 * The index examines [TasksState.Loaded.tasks] with `taskConfigs`, not with the task list, because the task list does not contain internal tasks.
 * The function does not start moon and does not read the disk.
 */
internal class InheritanceIndex private constructor(
    private val projects: Map<String, ProjectInheritance>,
    private val projectFiles: Map<Path, String>,
    private val files: Map<Path, FileInheritance>,
) {
    /** Gives the inheritance of the project [projectId], or null if the project has no inherited tasks. */
    fun project(projectId: String): ProjectInheritance? = projects[projectId]

    /** Gives the ID of the project with the configuration file [file], or null if [file] is not the configuration file of a project. */
    fun projectOfFile(file: Path): String? = projectFiles[file]

    /** Gives the inheritance of the inherited file [file], or null if no project of the load has this file in `inherited.layers`. */
    fun file(file: Path): FileInheritance? = files[file]

    companion object {
        fun of(state: TasksState.Loaded): InheritanceIndex {
            val configs = state.tasks.taskConfigs
            val projects = LinkedHashMap<String, ProjectInheritance>()
            for (projectId in configs.keys.sortedWith(ID_ORDER)) {
                projectInheritance(projectId, configs.getValue(projectId), state)?.let { projects[projectId] = it }
            }
            val rows = projects.flatMap { (projectId, project) ->
                project.inherited.flatMap { task -> task.files.map { file -> FileRow(file, projectId, task, project.merged[task.localId]) } }
            }.groupBy { it.file }
            // All known inherited files have an entry. Thus a file that no project inherits after the filters has the count 0.
            val files = state.configFiles.inherited.values.associateWith { fileInheritance(rows[it].orEmpty(), state.taskGraph) }
            val projectFiles = state.configFiles.projects.entries.associate { (id, file) -> file to id }
            return InheritanceIndex(projects, projectFiles, files)
        }

        /** Gives the inheritance of a project, or null if the project has no inherited tasks. */
        private fun projectInheritance(projectId: String, config: TaskConfigs, state: TasksState.Loaded): ProjectInheritance? {
            val inherited = inheritedTasks(config, state.configFiles.inherited)
            if (inherited.isEmpty()) return null
            val merged = inherited.filter { it.localId in config.ownTasks }.groupBy { it.localId }.mapValues { (localId, tasks) ->
                // The task graph has the values after the merge, also for internal tasks. If moon task-graph failed, use the values from moon query projects.
                val strategies = state.taskGraph?.mergeStrategies?.get(taskTarget(projectId, localId)) ?: config.mergeStrategies[localId].orEmpty()
                MergedTask(tasks, strategies)
            }
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

        /** Gives the inheritance of one file from its [rows]. The rows are in [ID_ORDER] of the projects. */
        private fun fileInheritance(rows: List<FileRow>, graph: TaskGraph?) = FileInheritance(
            rows.map { it.projectId }.distinct(),
            rows.groupBy { it.task.globalId }.mapValues { (_, tasks) ->
                val mergedIn = tasks.mapNotNull { row -> row.merged?.let { MergingProject(row.projectId, row.task.localId, it.strategies) } }
                val targets = tasks.map { taskTarget(it.projectId, it.task.localId) }
                val dependents = targets.mapNotNull { target -> graph?.dependents?.get(target)?.let { target to it } }
                val inheritedBy = tasks.associate { it.projectId to it.task.localId }
                GlobalTaskInheritance(inheritedBy, mergedIn, dependents.toMap().toSortedMap(ID_ORDER))
            },
        )
    }

    /** One inherited task of a project in one file. [merged] is the merged task of the project, or null if the task does not merge. */
    private class FileRow(val file: Path, val projectId: String, val task: InheritedTask, val merged: MergedTask?)
}
