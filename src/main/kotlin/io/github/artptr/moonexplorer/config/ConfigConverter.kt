package io.github.artptr.moonexplorer.config

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Reads the PSI of a moon configuration file in one format and gives a [ConfigSyntax].
 * To support one more format, add a converter. Then register the converter in the descriptor file of the format. Refer to ADR 0005.
 */
interface ConfigConverter {
    /** Gives the structure of [file], or null if the converter cannot read the file. Call this function in a read action. */
    fun convert(file: PsiFile): ConfigSyntax?

    /**
     * Gives the change of the text that removes [entry] of `workspace.inheritedTasks`, or null if the converter cannot remove it.
     * If the entry is the last entry of its list or map, the list or map becomes empty, for example `include: []`.
     * Thus an empty `include` list continues to remove all global tasks. Call this function in a read action.
     */
    fun inheritedTasksEntryRemoval(entry: InheritedTasksEntry): ConfigTextChange?
}

/**
 * A change of the text of a configuration file: [text] replaces the text in [range].
 * A converter gives a change of the text, not a change of the PSI. Thus the formatter does not change the other text of the file.
 */
class ConfigTextChange(val range: TextRange, val text: String) {
    /** Gives [fileText] after the change. */
    fun applyTo(fileText: String): String = range.replace(fileText, text)
}

/** The extension point `configConverter`. Each language has one converter. */
private val CONVERTERS = LanguageExtension<ConfigConverter>("io.github.artptr.moonexplorer.configConverter")

private val SYNTAX_KEY = Key.create<CachedValue<ConfigSyntax?>>("io.github.artptr.moonexplorer.configSyntax")

/**
 * Gives the structure of [file], or null if no converter can read the file. The IDE keeps the result until the file changes.
 * Call this function in a read action.
 */
fun configSyntax(file: PsiFile): ConfigSyntax? = CachedValuesManager.getCachedValue(file, SYNTAX_KEY) {
    CachedValueProvider.Result.create(CONVERTERS.forLanguage(file.language)?.convert(file), file)
}

/**
 * Gives the change that removes the entry of `workspace.inheritedTasks` with the first leaf [element], with the converter of its file.
 * Gives null if [element] is not the first leaf of an entry. Refer to [ConfigConverter.inheritedTasksEntryRemoval].
 */
fun inheritedTasksEntryRemoval(element: PsiElement): ConfigTextChange? {
    val file = element.containingFile
    val entry = configSyntax(file)?.inheritedTasks?.firstOrNull { it.element == element } ?: return null
    return CONVERTERS.forLanguage(file.language)?.inheritedTasksEntryRemoval(entry)
}

/** Gives the first leaf of the task key [taskId] in [file], or null if the file has no converter or does not contain the key. Call this function in a read action. */
fun taskKeyElement(file: PsiFile, taskId: String): PsiElement? = configSyntax(file)?.taskKeys?.firstOrNull { it.id == taskId }?.element
