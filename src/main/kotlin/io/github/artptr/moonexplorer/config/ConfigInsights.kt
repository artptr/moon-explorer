package io.github.artptr.moonexplorer.config

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.*
import java.nio.file.Path

/**
 * An item of the list of a hint or of a gutter icon. A click opens [target]. If [target] is null, the user cannot select the item.
 * [group] is the text above the items of a group, and [secondary] is the text at the right of the item.
 */
data class HintItem(val text: String, val target: SourceFile?, val group: String? = null, val secondary: String? = null)

/**
 * The list that a hint or a gutter icon opens. [items] makes the items each time that you call it. Thus a pass of the analysis does not make the items.
 * The hints call [items] only when the user clicks a hint. [empty] is true if [items] gives no items.
 * The class contains no PSI. Thus the IDE can keep it after the file changes. The function [makeItems] must not keep PSI.
 */
class HintList(val title: String, val empty: Boolean, private val makeItems: () -> List<HintItem>) {

    /** Makes a list with the items [items]. */
    constructor(title: String, items: List<HintItem>) : this(title, items.isEmpty(), { items })

    /** Makes the items of the list. */
    fun items(): List<HintItem> = makeItems()
}

/**
 * A Code Vision hint on the line of [element]. [label] is the name of the hint when the IDE joins it with other hints on one line.
 * The label of a hint on a task key is the task ID, without the quotes of the key.
 */
class ConfigHint(val element: PsiElement, val text: String, val list: HintList, val label: String = element.text)

/**
 * A gutter icon on [element], a leaf of a task key. If [local] is true, the key is a local task that merges with a global task.
 * If [local] is false, the key is a global task that merges with local tasks.
 */
class ConfigGutterMark(val element: PsiElement, val tooltip: String, val list: HintList, val local: Boolean)

/**
 * Joins the syntax of the configuration file [file] with the loaded data. The function finds the data with the task IDs.
 * Thus the hints stay on the correct lines while the user types. The numbers change after the next load.
 * [syntax] contains PSI. Use this class only in a read action. Only [configInsights] keeps it, in a cached value of the file.
 * The lists of the hints do not use this class. Thus a click lambda keeps only the state, a path or a project ID, and a task ID.
 */
class ConfigInsights internal constructor(private val syntax: ConfigSyntax, private val state: TasksState.Loaded, private val file: Path) {

    private val index = state.inheritanceIndex

    private val global: FileInheritance? = index.file(file)

    private val projectId: String? = index.projectOfFile(file)

    /** The global tasks that the project of this file can inherit, from `inherited.layers`. The value is null if the file is not the configuration file of a project with data. */
    private val layers: Map<String, List<String>>? = projectId?.let { state.tasks.taskConfigs[it]?.layers }

    /**
     * Gives the entries of `workspace.inheritedTasks` with a global task ID that no inherited file of the project contains.
     * moon writes in `inherited.layers` all the global tasks that the project can inherit, also the tasks that the entries remove.
     * Thus an entry with a correct ID is not in the result. If the plugin has no data for the project, the result is empty.
     */
    fun unknownInheritedTasks(): List<InheritedTasksEntry> {
        val known = layers ?: return emptyList()
        return syntax.inheritedTasks.filter { it.globalId !in known }
    }

    /** Gives the entry of `workspace.inheritedTasks` with the first leaf [element], or null if [element] is not the first leaf of an entry. */
    fun inheritedTasksEntry(element: PsiElement): InheritedTasksEntry? = syntax.inheritedTasksEntry(element)

    /**
     * Gives the inherited files of the project that contain the global task [globalId], in the sequence that moon uses.
     * The result also contains the files of the tasks that the project does not inherit, for example an excluded task.
     * The function does not read the disk. Thus it is possible that a file in the result is not on the disk.
     */
    fun globalTaskFiles(globalId: String): List<SourceFile> =
        layers?.get(globalId).orEmpty().mapNotNull { layer ->
            (state.configFiles.inherited[layer] ?: resolveInRoot(state.root, layer))?.let { sourceFile(it, globalId) }
        }.distinctBy { it.path }

    /** Gives the global task IDs that the project can inherit, in [ID_ORDER]. The list is empty if the plugin has no data for the project. */
    fun globalTaskIds(): List<String> = layers?.keys.orEmpty().sortedWith(ID_ORDER)

    /**
     * Gives the completion variants for [entry]: the global task IDs that the project can inherit, in [ID_ORDER].
     * The result does not contain the IDs of the other entries of the same list or map. For example, an item of `exclude` does not give
     * the IDs that `exclude` already contains. The IDs of `include` and `rename` remain in the result.
     */
    fun completionIds(entry: InheritedTasksEntry): List<String> {
        val used = syntax.inheritedTasks.filter { it !== entry && it::class == entry::class }.mapTo(HashSet()) { it.globalId }
        return globalTaskIds().filter { it !in used }
    }

    /**
     * Gives the hints of the inherited tasks.
     *
     * A file in `.moon/tasks/` has one hint at the top of the file. An item opens the configuration file of the project at the first line.
     * A project can inherit many tasks of the file, and the inherited tasks are not in its configuration file. Thus the item has no task key.
     * The secondary text of an item gives the task IDs in the project.
     *
     * The configuration file of a project has a hint above the key `tasks`. A project without the key `tasks` has the hint at the top of the file.
     */
    fun inheritedHints(): List<ConfigHint> {
        val project = projectId
        if (project != null) return listOfNotNull(inheritedTasksHint(project))
        val inheritance = global ?: return emptyList()
        val element = syntax.firstElement ?: return emptyList()
        val state = state
        val file = file
        val count = inheritance.projects.size
        val list = HintList(MoonExplorerBundle.message("hint.inheritedBy.title", state.workspacePath(file)), count == 0) { inheritedByItems(state, file) }
        return listOf(ConfigHint(element, MoonExplorerBundle.message("hint.inheritedBy", count), list))
    }

    private fun inheritedTasksHint(project: String): ConfigHint? {
        val inherited = index.project(project)?.inherited.orEmpty()
        val element = syntax.tasksKey ?: syntax.firstElement
        if (inherited.isEmpty() || element == null) return null
        val state = state
        val count = inherited.mapTo(HashSet()) { it.localId }.size
        val empty = inherited.all { it.files.isEmpty() }
        val list = HintList(MoonExplorerBundle.message("hint.inheritedTasks.title", project), empty) { inheritedTaskItems(state, project) }
        return ConfigHint(element, MoonExplorerBundle.message("hint.inheritedTasks", count), list)
    }

    /** The hint above each global task that merges with one or more local tasks, or above each local task that merges with a global task. */
    fun mergedHints(): List<ConfigHint> = merges.map { ConfigHint(it.key.element, it.text, it.list, it.key.id) }

    /** The gutter icons of the file. The tooltip of a local task also gives the merge strategies. */
    fun gutterMarks(): List<ConfigGutterMark> = merges.map { merge ->
        val tooltip = merge.strategies?.let { withStrategies(merge.text, it) } ?: merge.text
        ConfigGutterMark(merge.key.element, tooltip, merge.list, local = merge.strategies != null)
    }

    /** A task key that merges. [strategies] is not null only for a local task. */
    private class Merge(val key: ConfigKey, val text: String, val list: HintList, val strategies: Map<String, String>?)

    /** The hints and the gutter icons use the same merges. Thus the insights calculate them one time. */
    private val merges: List<Merge> by lazy {
        val state = state
        val file = file
        val localMerges = projectId?.let { project ->
            val local = index.project(project)?.merged.orEmpty()
            syntax.taskKeys.mapNotNull { key ->
                val globalTasks = local[key.id] ?: return@mapNotNull null
                val localId = key.id
                val list = HintList(globalTaskListTitle(localId, globalTasks), globalTasks.all { it.files.isEmpty() }) { globalTaskItems(state, project, localId) }
                Merge(key, mergedWithText(globalTasks), list, state.mergeStrategies(project, localId))
            }
        }.orEmpty()
        val globalMerges = globalTaskKeys.mapNotNull { (key, task) ->
            if (task.mergedIn.isEmpty()) return@mapNotNull null
            val globalId = key.id
            val list = HintList(MoonExplorerBundle.message("hint.mergedIn.title", globalId), false) { mergedInItems(state, file, globalId) }
            Merge(key, MoonExplorerBundle.message("hint.mergedIn", task.mergedIn.size), list, null)
        }
        localMerges + globalMerges
    }

    /** The text gives only the file names. The list gives the paths from the workspace root. */
    private fun mergedWithText(globalTasks: List<InheritedTask>): String =
        MoonExplorerBundle.message("hint.mergedWith", globalTasks.flatMap { it.files }.distinct().joinToString(", ") { it.fileName.toString() })

    /** The title of the list of the global tasks [globalTasks] that merge with the local task [localId]. Refer to [globalTaskItems]. */
    private fun globalTaskListTitle(localId: String, globalTasks: List<InheritedTask>): String {
        val globalIds = globalTasks.map { it.globalId }.distinct()
        return if (globalIds.size > 1) MoonExplorerBundle.message("hint.globalTasks.title", localId) else MoonExplorerBundle.message("hint.globalTask.title", globalIds.single())
    }

    /**
     * The task keys of a file in `.moon/tasks/` with the inheritance of each global task. A key without data is not in the list.
     * The merged hints and the dependents hints use the same keys. Thus the insights find them one time.
     */
    private val globalTaskKeys: List<Pair<ConfigKey, GlobalTaskInheritance>> by lazy {
        val inheritance = global ?: return@lazy emptyList()
        syntax.taskKeys.mapNotNull { key -> inheritance.tasks[key.id]?.let { key to it } }
    }

    /**
     * The hint above each global task with one or more direct dependents. The hint counts each dependent one time.
     * The list has one group for each inherited target. If `moon task-graph` failed in the load, the file has no hints of this kind.
     */
    fun dependentsHints(): List<ConfigHint> {
        val state = state
        val file = file
        return globalTaskKeys.mapNotNull { (key, task) ->
            val count = dependentCount(state.dependents(task))
            if (count == 0) return@mapNotNull null
            val globalId = key.id
            val list = HintList(MoonExplorerBundle.message("hint.dependents.title", globalId), false) { dependentItems(state, file, globalId) }
            ConfigHint(key.element, MoonExplorerBundle.message("hint.dependents", count), list, globalId)
        }
    }
}

/*
 * The functions below make the items of the hint lists. A hint calls them only when the user clicks it.
 * They get only the state, a path or a project ID, and a task ID. Thus the click lambdas do not keep PSI or the lists.
 */

/** The items of the projects that inherit tasks from the file [file]. The secondary text of an item gives the task IDs in the project. */
private fun inheritedByItems(state: TasksState.Loaded, file: Path): List<HintItem> =
    state.inheritanceIndex.file(file)?.projects.orEmpty().map { (project, localIds) ->
        HintItem(project, projectSourceFile(state, project), secondary = localIds.joinToString(", "))
    }

/**
 * The items of the tasks that the project [project] inherits. The list has one group for each local task.
 * A task can come from more than one global task and more than one file. Thus the group has one item for each global task in each file.
 * The secondary text gives the global ID if the project renames the task.
 */
private fun inheritedTaskItems(state: TasksState.Loaded, project: String): List<HintItem> {
    val inherited = state.inheritanceIndex.project(project)?.inherited.orEmpty()
    return inherited.groupBy { it.localId }.toSortedMap(ID_ORDER).flatMap { (localId, tasks) ->
        tasks.flatMap { task ->
            task.files.map { globalFileItem(state, it, task.globalId, localId, task.globalId.takeIf { id -> id != localId }) }
        }
    }
}

/** The items of the global tasks that merge with the local task [localId] of [project]. If more than one global task merges, the list has a group for each global ID. */
private fun globalTaskItems(state: TasksState.Loaded, project: String, localId: String): List<HintItem> {
    val globalTasks = state.inheritanceIndex.project(project)?.merged?.get(localId).orEmpty()
    val group = globalTasks.mapTo(HashSet()) { it.globalId }.size > 1
    return globalTasks.flatMap { global ->
        global.files.map { globalFileItem(state, it, global.globalId, global.globalId.takeIf { group }) }
    }
}

/** The items of the projects that merge a local task with the global task [globalId] of the file [file]. The text also gives the merge strategies. */
private fun mergedInItems(state: TasksState.Loaded, file: Path, globalId: String): List<HintItem> =
    state.inheritanceIndex.file(file)?.tasks?.get(globalId)?.mergedIn.orEmpty().map {
        HintItem(withStrategies(it.projectId, state.mergeStrategies(it.projectId, it.localId)), projectSourceFile(state, it.projectId, it.localId))
    }

/** The items of the direct dependents of the global task [globalId] of the file [file]. The list has one group for each inherited target. */
private fun dependentItems(state: TasksState.Loaded, file: Path, globalId: String): List<HintItem> {
    val task = state.inheritanceIndex.file(file)?.tasks?.get(globalId) ?: return emptyList()
    return state.dependents(task).flatMap { (target, tasks) -> tasks.map { dependentItem(state, target, it) } }
}

/**
 * The item of the dependent [target] in the group [group]. The item opens the first file of [taskSourceFiles].
 * Thus a local task opens its key in the configuration file of the project. An inherited task opens its key in the file in `.moon/tasks/`.
 * If the plugin does not know the files of the task, the item opens the configuration file of the project.
 */
private fun dependentItem(state: TasksState.Loaded, group: String, target: String): HintItem {
    val file = taskSourceFiles(state, target).firstOrNull() ?: projectSourceFile(state, targetProjectId(target), targetTaskId(target))
    return HintItem(target, file, group, file?.path?.let(state::workspacePath))
}

/** An item that opens the global task [globalId] in the inherited file [file]. The text is the path from the workspace root. */
private fun globalFileItem(state: TasksState.Loaded, file: Path, globalId: String, group: String? = null, secondary: String? = null) =
    HintItem(state.workspacePath(file), sourceFile(file, globalId), group, secondary)

/** Gives [text] and the [strategies], for example `web (args: prepend, deps: replace)`. If the map is empty, the function gives only [text]. */
internal fun withStrategies(text: String, strategies: Map<String, String>): String {
    if (strategies.isEmpty()) return text
    return MoonExplorerBundle.message("hint.item.withStrategies", text, strategies.entries.joinToString(", ") { (field, value) -> "$field: $value" })
}

/** The insights of a file and the load that they use. Refer to [configInsights]. */
private class CachedInsights(val state: TasksState.Loaded?, val insights: ConfigInsights?)

private val INSIGHTS_KEY = Key.create<CachedValue<CachedInsights>>("io.github.artptr.moonexplorer.configInsights")

/**
 * Gives the insights of [file] from the last successful load. Gives null if the load does not contain the file, or if no converter can read the file.
 * The IDE calls this function for all files. Thus it examines the converter before it reads the cache. Call this function in a read action.
 *
 * The function keeps the result in a cached value of the file. Thus the providers, the line markers, and the inspection of one pass use one instance.
 * The cached value becomes incorrect when the PSI of the file, the loaded data, or the paths of the files change.
 */
fun configInsights(file: PsiFile): ConfigInsights? {
    // Most files of the IDE have no converter. This check is cheaper than the cache.
    if (!hasConfigConverter(file)) return null
    val service = file.project.service<MoonTasksService>()
    val state = service.state.value.lastLoaded
    val cached = CachedValuesManager.getCachedValue(file, INSIGHTS_KEY) {
        CachedValueProvider.Result.create(
            CachedInsights(state, state?.let { insightsOf(file, it) }),
            file, service.loadTracker, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
        )
    }
    // A new load can occur while the IDE calculates the cached value. Then the cached value has the old load, and the function does not use it.
    if (cached.state === state) return cached.insights
    return state?.let { insightsOf(file, it) }
}

/** Gives the insights of [file] with [state]. Gives null if [state] does not contain the file, or if no converter can read the file. */
private fun insightsOf(file: PsiFile, state: TasksState.Loaded): ConfigInsights? {
    val path = file.originalFile.virtualFile?.configFilePath() ?: return null
    if (state.inheritanceIndex.file(path) == null && state.inheritanceIndex.projectOfFile(path) == null) return null
    return configSyntax(file)?.let { ConfigInsights(it, state, path) }
}
