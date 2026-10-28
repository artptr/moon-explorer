package io.github.artptr.moonexplorer.yaml

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TaskKeyTargets
import io.github.artptr.moonexplorer.model.configFilePath
import io.github.artptr.moonexplorer.model.lastLoaded
import io.github.artptr.moonexplorer.model.taskKeyTargets
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.nio.file.Path

/** A task key in a YAML file. [file] is absolute and normalized. [taskId] is the key text without quotes. */
internal data class TaskKey(val file: Path, val taskId: String)

private const val TASKS_KEY = "tasks"

/**
 * Gives the task key of [element], or null. [element] must be the first leaf of a key below the top-level key `tasks:`.
 * The IDE attaches a gutter icon to a leaf element. Thus only one element of each key gives a value.
 * The function examines the PSI structure before the path. Thus it returns quickly for most elements.
 */
internal fun taskKeyAt(element: PsiElement): TaskKey? {
    if (element.firstChild != null) return null
    val keyValue = element.parent as? YAMLKeyValue ?: element.parent?.parent as? YAMLKeyValue ?: return null
    val key = keyValue.key ?: return null
    if (key !== element && key.firstChild !== element) return null
    val tasks = keyValue.parentMapping?.parent as? YAMLKeyValue ?: return null
    // The check of the document is cheaper than keyText, which makes a string. Most nested keys in other YAML files stop here.
    if (tasks.parentMapping?.parent !is YAMLDocument || tasks.keyText != TASKS_KEY) return null
    val file = element.containingFile?.originalFile?.virtualFile?.configFilePath() ?: return null
    return TaskKey(file, keyValue.keyText)
}

/** Gives the targets of the task key of [element] from the loaded data. The function does not start moon. Refer to [taskKeyTargets]. */
internal fun taskKeyTargetsAt(element: PsiElement): TaskKeyTargets? {
    val key = taskKeyAt(element) ?: return null
    val state = element.project.service<MoonTasksService>().state.value.lastLoaded ?: return null
    return taskKeyTargets(state, key.file, key.taskId)
}
