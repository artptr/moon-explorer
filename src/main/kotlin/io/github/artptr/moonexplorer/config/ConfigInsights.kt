package io.github.artptr.moonexplorer.config

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.FileInheritance
import io.github.artptr.moonexplorer.model.ID_ORDER
import io.github.artptr.moonexplorer.model.MergedTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.configFilePath
import io.github.artptr.moonexplorer.model.lastLoaded
import io.github.artptr.moonexplorer.model.sourceFile
import io.github.artptr.moonexplorer.model.workspacePath
import java.nio.file.Path

/**
 * An item of the list of a hint or of a gutter icon. A click opens [target]. If [target] is null, the user cannot select the item.
 * [group] is the text above the items of a group, and [secondary] is the text at the right of the item.
 */
data class HintItem(val text: String, val target: SourceFile?, val group: String? = null, val secondary: String? = null)

/** The list that a hint or a gutter icon opens. The class contains no PSI. Thus the IDE can keep it after the file changes. */
data class HintList(val title: String, val items: List<HintItem>)

/** A Code Vision hint on the line of [element]. */
class ConfigHint(val element: PsiElement, val text: String, val list: HintList)

/**
 * A gutter icon on [element], a leaf of a task key. If [local] is true, the key is a local task that merges with a global task.
 * If [local] is false, the key is a global task that merges with local tasks.
 */
class ConfigGutterMark(val element: PsiElement, val tooltip: String, val list: HintList, val local: Boolean)

/**
 * Joins the syntax of the configuration file [file] with the loaded data. The function finds the data with the task IDs.
 * Thus the hints stay on the correct lines while the user types. The numbers change after the next load.
 * [syntax] contains PSI. Use this class only in a read action, and do not keep it.
 */
class ConfigInsights internal constructor(private val syntax: ConfigSyntax, private val state: TasksState.Loaded, private val file: Path) {

    private val index get() = state.inheritanceIndex

    private val global: FileInheritance? get() = index.file(file)

    private val projectId: String? get() = index.projectOfFile(file)

    /**
     * Gives the hints of the inherited tasks.
     *
     * A file in `.moon/tasks/` has one hint at the top of the file. An item opens the configuration file of the project at the first line.
     * A project can inherit many tasks of the file, and the inherited tasks are not in its configuration file. Thus the item has no task key.
     * The secondary text of an item gives the task IDs in the project.
     *
     * The configuration file of a project has a hint above the key `tasks` and a hint on each entry of `workspace.inheritedTasks`.
     * A project without the key `tasks` has the hint at the top of the file.
     *
     * The texts give the paths from the workspace root, because two files can have the same name.
     */
    fun inheritedHints(): List<ConfigHint> {
        val project = projectId
        if (project != null) return listOfNotNull(inheritedTasksHint(project)) + entryHints(project)
        val inheritance = global ?: return emptyList()
        val element = syntax.firstElement ?: return emptyList()
        val items = inheritance.projects.map { (project, localIds) ->
            HintItem(project, projectFile(project, null), secondary = localIds.joinToString(", "))
        }
        val list = HintList(MoonExplorerBundle.message("hint.inheritedBy.title", state.workspacePath(file)), items)
        return listOf(ConfigHint(element, MoonExplorerBundle.message("hint.inheritedBy", items.size), list))
    }

    private fun inheritedTasksHint(project: String): ConfigHint? {
        val inherited = index.project(project)?.inherited.orEmpty()
        val element = syntax.tasksKey ?: syntax.firstElement
        if (inherited.isEmpty() || element == null) return null
        // One item for each local task. A task can come from more than one file. The item opens the first file, and the secondary text gives all files.
        val items = inherited.groupBy { it.localId }.toSortedMap(ID_ORDER).map { (localId, tasks) ->
            val first = tasks.first()
            val files = tasks.flatMap { it.files }.distinct()
            HintItem(localId, sourceFile(first.files.first(), first.globalId), secondary = paths(files))
        }
        val list = HintList(MoonExplorerBundle.message("hint.inheritedTasks.title", project), items)
        return ConfigHint(element, MoonExplorerBundle.message("hint.inheritedTasks", items.size), list)
    }

    /**
     * The hints on the entries of `workspace.inheritedTasks`. An entry that matches no global task of the project has a hint without a list.
     * Thus the user can see an incorrect task ID.
     */
    private fun entryHints(project: String): List<ConfigHint> {
        val layers = state.tasks.taskConfigs[project]?.layers.orEmpty()
        return syntax.inheritedTasks.map { entry ->
            val files = layers[entry.globalId].orEmpty().mapNotNull { state.configFiles.inherited[it] }.distinct()
            if (files.isEmpty()) {
                val text = MoonExplorerBundle.message("hint.entry.noMatch", entry.globalId)
                return@map ConfigHint(entry.element, text, HintList(text, emptyList()))
            }
            val names = paths(files)
            val text = when (entry) {
                is InheritedTasksEntry.Include -> MoonExplorerBundle.message("hint.entry.include", entry.globalId, names)
                is InheritedTasksEntry.Exclude -> MoonExplorerBundle.message("hint.entry.exclude", entry.globalId, names)
                is InheritedTasksEntry.Rename -> MoonExplorerBundle.message("hint.entry.rename", entry.globalId, names, entry.localId)
            }
            val items = files.map { HintItem(state.workspacePath(it), sourceFile(it, entry.globalId)) }
            ConfigHint(entry.element, text, HintList(MoonExplorerBundle.message("hint.globalTask.title", entry.globalId), items))
        }
    }

    /** The hint above each global task that merges with one or more local tasks, or above each local task that merges with a global task. */
    fun mergedHints(): List<ConfigHint> {
        val merged = localMerges().map { (key, task) -> ConfigHint(key.element, mergedWithText(task), globalTaskList(key, task)) }
        return merged + mergedInLists().map { (key, list) ->
            ConfigHint(key.element, MoonExplorerBundle.message("hint.mergedIn", list.items.size), list)
        }
    }

    /** The gutter icons of the file. */
    fun gutterMarks(): List<ConfigGutterMark> {
        val local = localMerges().map { (key, task) ->
            ConfigGutterMark(key.element, withStrategies(mergedWithText(task), task.strategies), globalTaskList(key, task), local = true)
        }
        return local + mergedInLists().map { (key, list) ->
            ConfigGutterMark(key.element, MoonExplorerBundle.message("hint.mergedIn", list.items.size), list, local = false)
        }
    }

    private fun localMerges(): List<Pair<ConfigKey, MergedTask>> {
        val merged = projectId?.let(index::project)?.merged.orEmpty()
        return syntax.taskKeys.mapNotNull { key -> merged[key.id]?.let { key to it } }
    }

    private fun mergedWithText(task: MergedTask): String =
        MoonExplorerBundle.message("hint.mergedWith", paths(task.globalTasks.flatMap { it.files }.distinct()))

    /**
     * The list of the global tasks that merge with the local task [key]. The title gives the global ID, because a project can rename the task.
     * If more than one global task merges, the list has a group for each global ID.
     */
    private fun globalTaskList(key: ConfigKey, task: MergedTask): HintList {
        val globalIds = task.globalTasks.map { it.globalId }.distinct()
        val group = globalIds.size > 1
        val items = task.globalTasks.flatMap { global ->
            global.files.map { HintItem(state.workspacePath(it), sourceFile(it, global.globalId), global.globalId.takeIf { group }) }
        }
        val title = if (group) MoonExplorerBundle.message("hint.globalTasks.title", key.id) else MoonExplorerBundle.message("hint.globalTask.title", globalIds.single())
        return HintList(title, items)
    }

    private fun paths(files: List<Path>): String = files.joinToString(", ") { state.workspacePath(it) }

    private fun mergedInLists(): List<Pair<ConfigKey, HintList>> {
        val inheritance = global ?: return emptyList()
        return syntax.taskKeys.mapNotNull { key ->
            val merged = inheritance.tasks[key.id]?.mergedIn.orEmpty()
            if (merged.isEmpty()) return@mapNotNull null
            val items = merged.map { HintItem(withStrategies(it.projectId, it.strategies), projectFile(it.projectId, it.localId)) }
            key to HintList(MoonExplorerBundle.message("hint.mergedIn.title", key.id), items)
        }
    }

    /** Gives the configuration file of [projectId], or null if the project has no configuration file. [taskId] is the key to open in the file. */
    private fun projectFile(projectId: String, taskId: String?): SourceFile? =
        state.configFiles.projects[projectId]?.let { sourceFile(it, taskId) }
}

/** Gives [text] and the [strategies], for example `web (args: prepend, deps: replace)`. If the map is empty, the function gives only [text]. */
internal fun withStrategies(text: String, strategies: Map<String, String>): String {
    if (strategies.isEmpty()) return text
    return MoonExplorerBundle.message("hint.item.withStrategies", text, strategies.entries.joinToString(", ") { (field, value) -> "$field: $value" })
}

/**
 * Gives the insights of [file] from the last successful load. Gives null if the load does not contain the file, or if no converter can read the file.
 * The IDE calls this function for all files. Thus it examines the path before it reads the PSI. Call this function in a read action.
 */
fun configInsights(file: PsiFile): ConfigInsights? {
    val state = file.project.service<MoonTasksService>().state.value.lastLoaded ?: return null
    val path = file.originalFile.virtualFile?.configFilePath() ?: return null
    if (state.inheritanceIndex.file(path) == null && state.inheritanceIndex.projectOfFile(path) == null) return null
    return configSyntax(file)?.let { ConfigInsights(it, state, path) }
}
