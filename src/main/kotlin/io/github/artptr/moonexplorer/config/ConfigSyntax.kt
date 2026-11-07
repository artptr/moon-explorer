package io.github.artptr.moonexplorer.config

import com.intellij.psi.PsiElement

/** A task key in a configuration file. [id] is the key without quotes. [element] is the first leaf of the key. The hints and the icons attach to this leaf. */
class ConfigKey(val id: String, val element: PsiElement)

/** An entry of `workspace.inheritedTasks` in the configuration file of a project. [element] is the first leaf of the entry. */
sealed interface InheritedTasksEntry {
    /** The global task ID of the entry. */
    val globalId: String
    val element: PsiElement

    /** An item of the list `include`. */
    class Include(override val globalId: String, override val element: PsiElement) : InheritedTasksEntry

    /** An item of the list `exclude`. */
    class Exclude(override val globalId: String, override val element: PsiElement) : InheritedTasksEntry

    /** A pair of the map `rename`. [localId] is the task ID in the project. */
    class Rename(override val globalId: String, val localId: String, override val element: PsiElement) : InheritedTasksEntry
}

/**
 * The structure of a moon configuration file, without the format of the file. A [ConfigConverter] makes it from the PSI.
 * The class contains only the syntax of the file. It does not tell if the file is global or local. Refer to rule 14 in ADR 0005.
 *
 * [firstElement] is the first leaf of the file that is not white space, or null if the file is empty.
 * [tasksKey] is the first leaf of the top-level key `tasks`, or null if the file does not have this key.
 * [taskKeys] contains the keys below `tasks`. [inheritedTasks] contains the entries of `workspace.inheritedTasks`.
 */
class ConfigSyntax(
    val firstElement: PsiElement?,
    val tasksKey: PsiElement?,
    val taskKeys: List<ConfigKey>,
    val inheritedTasks: List<InheritedTasksEntry>,
)
