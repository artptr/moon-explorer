package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.artptr.moonexplorer.MoonIcons

/**
 * Opens the global task of an entry of `workspace.inheritedTasks`, for example with **Go to Declaration**.
 * If more than one inherited file contains the global task, the IDE shows a list of the files.
 * The handler gives the task key in each file. If the file has no converter or does not contain the key, it gives the file.
 * The IDE calls the handler for all files. Thus [configInsights] examines the path before it reads the PSI.
 */
class MoonInheritedTasksGotoDeclarationHandler : GotoDeclarationHandler, DumbAware {

    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val element = source ?: return null
        val insights = configInsights(element.containingFile ?: return null) ?: return null
        val entry = insights.inheritedTasksEntry(element) ?: return null
        val manager = PsiManager.getInstance(element.project)
        val targets = insights.globalTaskFiles(entry.globalId).mapNotNull { target ->
            val file = LocalFileSystem.getInstance().findFileByNioFile(target.path)?.let(manager::findFile) ?: return@mapNotNull null
            val key = target.taskId?.let { taskKeyElement(file, it) }
            // The named parent of the key gives the name and the file in the list of the IDE, for example a YAML key and value.
            key?.let { PsiTreeUtil.getParentOfType(it, PsiNamedElement::class.java) ?: it } ?: file
        }
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

/**
 * Gives the global task IDs that the project can inherit as completion variants in the entries of `workspace.inheritedTasks`.
 * The completion operates in an entry that the converter finds, for example an item of `include` or a key of `rename`.
 * It does not give the IDs that the list or map of the entry already contains. Refer to [ConfigInsights.completionIds].
 * The class does not use the classes of a language plugin. Register it in the descriptor file of each format with a [ConfigConverter].
 */
class MoonInheritedTasksCompletionContributor : CompletionContributor(), DumbAware {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val insights = configInsights(position.containingFile) ?: return
        val entry = insights.inheritedTasksEntry(position) ?: return
        // A task ID can contain characters that are not in a Java identifier, for example `-`. Thus the prefix is the text before the caret.
        val start = position.textRange.startOffset
        val prefix = position.text.substring(0, (parameters.offset - start).coerceIn(0, position.textLength)).trimStart('\'', '"')
        val ids = result.withPrefixMatcher(prefix)
        insights.completionIds(entry).forEach { ids.addElement(LookupElementBuilder.create(it).withIcon(MoonIcons.Moon)) }
    }
}
