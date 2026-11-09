package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.SourceFile
import javax.swing.Icon

/**
 * Shows the inheritance icons of [ConfigInsights.gutterMarks] in the gutter. The class does not use the classes of a language plugin.
 * The IDE uses a line marker provider for one language. Thus register this class in the descriptor file of each format with a [ConfigConverter].
 * The provider reads the insights one time for each file, in the slow pass. The IDE does the same for the override icons of Java.
 */
class MoonInheritanceLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {

    override fun getName(): String = MoonExplorerBundle.message("gutter.inheritance.name")

    override fun getIcon(): Icon = AllIcons.Gutter.OverridenMethod

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
        val marks = configInsights(file)?.gutterMarks().orEmpty().associateBy { it.element }
        for (element in elements) {
            val mark = marks[element] ?: continue
            result += lineMarker(element, mark)
        }
    }

    private fun lineMarker(element: PsiElement, mark: ConfigGutterMark): LineMarkerInfo<PsiElement> {
        val tooltip = mark.tooltip
        // The handler must not keep PSI of the mark. The IDE gives the element to the handler.
        val list = mark.list
        val single = directTarget(mark)
        val navigation = GutterIconNavigationHandler<PsiElement> { mouse, target ->
            single?.let {
                openConfigTarget(target.project, it)
                return@GutterIconNavigationHandler
            }
            // Use the editor of the file of the icon. The selected editor can show a different file.
            val editor = PsiEditorUtil.findEditor(target) ?: return@GutterIconNavigationHandler
            showHintList(editor, list, mouse)
        }
        val markIcon = if (mark.local) AllIcons.Gutter.OverridingMethod else AllIcons.Gutter.OverridenMethod
        return LineMarkerInfo(element, element.textRange, markIcon, { tooltip }, navigation, GutterIconRenderer.Alignment.RIGHT, { tooltip })
    }
}

/**
 * Gives the target that a click on [mark] opens without a list, or null if the click shows the list.
 * A local task with one global task in one file opens the global task. Then the user has no selection to make.
 */
internal fun directTarget(mark: ConfigGutterMark): SourceFile? = mark.list.items.singleOrNull()?.target?.takeIf { mark.local }
