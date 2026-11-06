package io.github.artptr.moonexplorer.config

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.FileInheritance
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.configFilePath
import io.github.artptr.moonexplorer.model.lastLoaded
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

/** A gutter icon on [element], a leaf of a task key. */
class ConfigGutterMark(val element: PsiElement, val tooltip: String, val list: HintList)

/**
 * Joins the syntax of the configuration file [file] with the loaded data. The function finds the data with the task IDs.
 * Thus the hints stay on the correct lines while the user types. The numbers change after the next load.
 * [syntax] contains PSI. Use this class only in a read action, and do not keep it.
 */
class ConfigInsights internal constructor(private val syntax: ConfigSyntax, private val state: TasksState.Loaded, private val file: Path) {

    private val index get() = state.inheritanceIndex

    private val global: FileInheritance? get() = index.file(file)

    /** The hint at the top of a file in `.moon/tasks/`. */
    fun inheritedHints(): List<ConfigHint> {
        val inheritance = global ?: return emptyList()
        val element = syntax.firstElement ?: return emptyList()
        val fileName = file.fileName.toString()
        val items = inheritance.projects.map { HintItem(it, projectFile(it, null)) }
        val list = HintList(MoonExplorerBundle.message("hint.inheritedBy.title", fileName), items)
        return listOf(ConfigHint(element, MoonExplorerBundle.message("hint.inheritedBy", items.size), list))
    }

    /** The hint above each global task that merges with one or more local tasks. */
    fun mergedHints(): List<ConfigHint> = mergedInLists().map { (key, list) ->
        ConfigHint(key.element, MoonExplorerBundle.message("hint.mergedIn", list.items.size), list)
    }

    /** The gutter icons of the file. */
    fun gutterMarks(): List<ConfigGutterMark> = mergedInLists().map { (key, list) ->
        ConfigGutterMark(key.element, MoonExplorerBundle.message("hint.mergedIn", list.items.size), list)
    }

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
        state.configFiles.projects[projectId]?.let { SourceFile(it, taskId) }
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
