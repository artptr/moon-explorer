package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.github.artptr.moonexplorer.MoonExplorerBundle

/** The group of the hints in **Settings | Editor | Inlay Hints**. All providers use one group. Thus the settings show one item. */
internal const val MOON_CODE_VISION_GROUP = "moon.inheritance"

/**
 * Shows the hints of [ConfigInsights] in the configuration files. The provider uses only the loaded data. It does not start moon.
 * After a load, [io.github.artptr.moonexplorer.ui.RunMarkersRefresher] updates the hints.
 *
 * Each kind of hint has its own provider. Thus a provider can set the sequence of its hints on a line with [relativeOrderings].
 * A hint with an empty list has no click action. Thus the user cannot open an empty list.
 */
abstract class MoonCodeVisionProvider : DaemonBoundCodeVisionProvider, DumbAware {

    override val groupId: String get() = MOON_CODE_VISION_GROUP

    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Default

    override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()

    /** Gives the hints of this provider. */
    protected abstract fun hints(insights: ConfigInsights): List<ConfigHint>

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> =
        configInsights(file)?.let(::hints).orEmpty().map { hint -> hint.element.textRange to codeVisionEntry(hint.text, hint.list, id) }
}

/** Gives the entry of a hint with [text] and [list]. The click handler must not keep PSI. [HintList] contains no PSI. */
internal fun codeVisionEntry(text: String, list: HintList, providerId: String): CodeVisionEntry =
    if (list.items.isEmpty()) {
        TextCodeVisionEntry(text, providerId)
    } else {
        ClickableTextCodeVisionEntry(text, providerId, { mouse, clicked -> showHintList(clicked, list, mouse) })
    }

/** The hint with the number of projects that inherit tasks from the file. */
class MoonInheritedCodeVisionProvider : MoonCodeVisionProvider() {
    override val id: String get() = "moon.inherited"
    override val name: String get() = MoonExplorerBundle.message("codeVision.inherited.name")

    /** In a flow mapping, the hint at the top of the file and a hint of a task can be on one line. This hint is the first. */
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)
    override fun hints(insights: ConfigInsights): List<ConfigHint> = insights.inheritedHints()
}

/** The hint for a global task that merges with local tasks. */
class MoonMergedCodeVisionProvider : MoonCodeVisionProvider() {
    override val id: String get() = "moon.merged"
    override val name: String get() = MoonExplorerBundle.message("codeVision.merged.name")
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter("moon.inherited"))
    override fun hints(insights: ConfigInsights): List<ConfigHint> = insights.mergedHints()
}

/** Adds the hints of the plugin to **Settings | Editor | Inlay Hints**. */
class MoonCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId: String get() = MOON_CODE_VISION_GROUP
    override val groupName: String get() = MoonExplorerBundle.message("codeVision.group.name")
    override val description: String get() = MoonExplorerBundle.message("codeVision.group.description")
}
