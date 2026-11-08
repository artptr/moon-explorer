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

    /**
     * The editor shows only one entry of a provider on a line. Thus the function joins the hints on one line into one entry.
     * Example: the items of `exclude: ['format', 'lint']` are on one line.
     */
    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        val hints = configInsights(file)?.let(::hints).orEmpty()
        return joinHintsOnOneLine(hints) { editor.document.getLineNumber(it.element.textRange.startOffset) }
            .map { hint -> hint.element.textRange to codeVisionEntry(hint.text, hint.list, id) }
    }
}

/**
 * Joins the hints with the same value of [lineOf] into one hint. The joined hint is on the element of the first hint.
 * The text contains the text of each hint. The list has a group for each hint with the text of the hint. A hint without a list has an item that the user cannot select.
 * A line with one hint keeps the hint.
 */
internal fun joinHintsOnOneLine(hints: List<ConfigHint>, lineOf: (ConfigHint) -> Int): List<ConfigHint> =
    hints.groupBy(lineOf).values.map { line ->
        if (line.size == 1) return@map line.single()
        val text = line.map { it.text }.reduce { joined, next -> MoonExplorerBundle.message("hint.joined", joined, next) }
        val items = line.flatMap { hint ->
            if (hint.list.items.isEmpty()) {
                listOf(HintItem(hint.text, null))
            } else {
                hint.list.items.map { item ->
                    val group = item.group?.let { MoonExplorerBundle.message("hint.joined.group", hint.text, it) } ?: hint.text
                    item.copy(group = group)
                }
            }
        }
        ConfigHint(line.first().element, text, HintList(MoonExplorerBundle.message("hint.joined.title"), items))
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
