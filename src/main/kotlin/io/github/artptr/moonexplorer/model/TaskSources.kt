package io.github.artptr.moonexplorer.model

import com.intellij.openapi.util.io.FileUtil
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * A configuration file of a task or a project. [path] is absolute.
 * [taskId] is the task to find in the file. If [taskId] is null, the IDE opens the file at the first line.
 */
data class SourceFile(val path: Path, val taskId: String?)

private val YAML_EXTENSIONS = setOf("yml", "yaml")

/** Gives true if [path] is a YAML file. The plugin finds the line of a task only in YAML files. */
fun isYamlFile(path: Path): Boolean = path.extension.lowercase() in YAML_EXTENSIONS

/** The workspace root of the state, absolute and normalized. */
private val TasksState.Loaded.normalizedRoot: Path get() = root.toAbsolutePath().normalize()

/** Gives the path of [path] from the workspace root, with `/` as the separator. Example: `.moon/tasks/all.yml`. */
fun TasksState.Loaded.workspacePath(path: Path): String {
    val root = normalizedRoot
    return FileUtil.toSystemIndependentName((if (path.startsWith(root)) root.relativize(path) else path).toString())
}

/**
 * Gives the configuration file of the project in [projectDir], for example `moon.yml`. Gives null if the project has no configuration file.
 * The function examines the extensions in the sequence of [MoonWorkspaceLocator.CONFIG_FILE_EXTENSIONS].
 * The function reads the disk. Call it on a background thread.
 */
fun findProjectConfigFile(projectDir: Path): Path? = MoonWorkspaceLocator.findConfigFile(projectDir, "moon")

/**
 * Gives the files that contain the configuration of [task]. The configuration file of the project is the first file.
 * The inherited files from `.moon/tasks/` follow, in the sequence that moon uses. The list contains only the files that exist.
 * The function reads the disk. Call it on a background thread.
 */
fun taskSourceFiles(state: TasksState.Loaded, task: MoonTask): List<SourceFile> {
    val projectId = targetProjectId(task.target)
    val configs = state.tasks.taskConfigs[projectId] ?: TaskConfigs()
    val root = state.normalizedRoot
    val own = if (task.id in configs.ownTasks) projectConfigFile(state, root, projectId) else null
    val layers = configs.layers[task.id].orEmpty().mapNotNull { resolveInRoot(root, it) }
    return (listOfNotNull(own) + layers)
        .distinct()
        .filter { it == own || Files.isRegularFile(it) }
        .map { SourceFile(it, task.id) }
}

/**
 * Gives the configuration file of the project [projectId]. The IDE opens it at the first line.
 * Gives null if the project has no configuration file. The function reads the disk. Call it on a background thread.
 */
fun projectSourceFile(state: TasksState.Loaded, projectId: String): SourceFile? =
    projectConfigFile(state, state.normalizedRoot, projectId)?.let { SourceFile(it, null) }

/** Gives the configuration file of the project [projectId]. [root] is the normalized workspace root of [state]. */
private fun projectConfigFile(state: TasksState.Loaded, root: Path, projectId: String): Path? {
    val source = state.tasks.sources.firstOrNull { it.id == projectId } ?: return null
    val dir = resolveSourceDir(root, source) ?: return null
    return findProjectConfigFile(dir)
}

private val TASKS_LINE = Regex("""^(?:tasks|'tasks'|"tasks")\s*:\s*(?:#.*)?$""")
private val KEY = Regex("""^(?:'((?:[^']|'')*)'|"([^"]*)"|([^\s#'"][^:#]*?))\s*:(?:\s|$)""")

/**
 * Gives the offset of the key [taskId] below the top-level key `tasks:` in the YAML text [text]. Gives null if the text does not contain the key.
 *
 * The function does a text search and does not parse YAML. Thus it finds task keys only in a block mapping with one task key on each line.
 * The first line below `tasks:` sets the indentation of the task keys. The function ignores the keys with more indentation, for example `deps:`.
 * The search stops at the next top-level key.
 */
fun findTaskKeyOffset(text: CharSequence, taskId: String): Int? {
    var inTasks = false
    var indent: Int? = null
    var start = 0
    while (start <= text.length) {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val line = text.subSequence(start, end).toString().removeSuffix("\r")
        val lineStart = start
        start = end + 1
        if (!inTasks) {
            inTasks = TASKS_LINE.matches(line)
            continue
        }
        val content = line.trimStart(' ')
        if (content.isEmpty() || content.startsWith('#')) continue
        val lineIndent = line.length - content.length
        if (lineIndent == 0) return null
        if (indent == null) indent = lineIndent
        if (lineIndent == indent && keyOf(content) == taskId) return lineStart + lineIndent
    }
    return null
}

/** Gives the key at the start of [content], or null if [content] does not start with a key. The function removes the quotes. */
private fun keyOf(content: String): String? {
    val match = KEY.find(content) ?: return null
    return match.groups[1]?.value?.replace("''", "'") ?: match.groups[2]?.value ?: match.groupValues[3]
}
