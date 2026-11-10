package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.github.artptr.moonexplorer.MoonExplorerBundle

/**
 * Marks the entries of `workspace.inheritedTasks` with a global task ID that no inherited file of the project contains.
 * Refer to [ConfigInsights.unknownInheritedTasks]. The class does not use the classes of a language plugin.
 * Register it in the descriptor file of each format with a [ConfigConverter].
 */
class MoonInheritedTasksInspection : LocalInspectionTool(), DumbAware {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        return configInsights(file)?.unknownInheritedTasks()?.map { entry ->
            manager.createProblemDescriptor(
                entry.element,
                MoonExplorerBundle.message("inspection.inheritedTasks.unknown", entry.globalId),
                isOnTheFly,
                FIXES,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }?.toTypedArray()
    }

    private companion object {
        /** The fix has no state. Thus all problems use the same fix. */
        val FIXES = arrayOf<LocalQuickFix>(RemoveInheritedTasksEntryFix())
    }
}

/**
 * Removes the entry of `workspace.inheritedTasks` of the problem. Refer to [ConfigConverter.inheritedTasksEntryRemoval].
 * The fix changes the text of the document, not the PSI. Thus the fix makes its preview from the text.
 */
class RemoveInheritedTasksEntryFix : LocalQuickFix {

    override fun getFamilyName(): String = MoonExplorerBundle.message("inspection.inheritedTasks.remove")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val change = inheritedTasksEntryRemoval(element) ?: return
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(element.containingFile) ?: return
        manager.doPostponedOperationsAndUnblockDocument(document)
        document.replaceString(change.range.startOffset, change.range.endOffset, change.text)
        manager.commitDocument(document)
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        val element = previewDescriptor.psiElement ?: return IntentionPreviewInfo.EMPTY
        val change = inheritedTasksEntryRemoval(element) ?: return IntentionPreviewInfo.EMPTY
        val file = element.containingFile
        val text = file.text
        return IntentionPreviewInfo.CustomDiff(file.fileType, file.name, text, change.applyTo(text))
    }
}
