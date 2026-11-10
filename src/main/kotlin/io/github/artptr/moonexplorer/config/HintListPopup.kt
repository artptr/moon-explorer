package io.github.artptr.moonexplorer.config

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.StatusText
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.IdentityHashMap

/** The list of a hint or of a gutter icon. The IDE calls [choose] with the [SourceFile] of the item that the user selects. */
internal class HintListStep(list: HintList, private val choose: (SourceFile) -> Unit) :
    BaseListPopupStep<HintItem>(list.title, list.items), ListPopupStepEx<HintItem> {

    /** The first item of each group. The set compares the items by identity, because two items can be equal. */
    private val groupStarts: MutableSet<HintItem> = Collections.newSetFromMap(IdentityHashMap())

    init {
        list.items.forEachIndexed { i, item -> if (item.group != null && (i == 0 || list.items[i - 1].group != item.group)) groupStarts += item }
    }

    override fun getTextFor(value: HintItem): String = value.text

    override fun isSelectable(value: HintItem): Boolean = value.target != null

    override fun getSecondaryTextFor(value: HintItem): String? = value.secondary

    override fun getTooltipTextFor(value: HintItem): String? = null

    override fun setEmptyText(emptyText: StatusText) = Unit

    /** The first item of each group has a separator with the text of the group. */
    override fun getSeparatorAbove(value: HintItem): ListSeparator? {
        val group = value.group ?: return null
        return if (value in groupStarts) ListSeparator(group) else null
    }

    override fun onChosen(selectedValue: HintItem, finalChoice: Boolean): PopupStep<*>? {
        selectedValue.target?.let { target -> return doFinalStep { choose(target) } }
        return PopupStep.FINAL_CHOICE
    }
}

/** Shows [list] at the position of [mouse]. If [mouse] is null, the function shows the list at the caret of [editor]. Call this function on the EDT. */
internal fun showHintList(editor: Editor, list: HintList, mouse: MouseEvent?) {
    val project = editor.project ?: return
    val popup = JBPopupFactory.getInstance().createListPopup(HintListStep(list) { openConfigTarget(project, it) })
    if (mouse != null) popup.show(RelativePoint(mouse)) else popup.showInBestPositionFor(editor)
}

/** Starts to open [target] on a background thread. Refer to [openConfigFile]. */
internal fun openConfigTarget(project: Project, target: SourceFile) {
    project.service<MoonTasksService>().scope.launch { openConfigFile(project, target) }
}

/**
 * Opens [target] in the editor. If [SourceFile.taskId] is not null, the editor shows the task key. The function finds it with the converter of the file.
 * If the file has no converter or does not contain the key, the editor shows the first line.
 * If the file is now missing or is a directory, the function does nothing.
 */
internal suspend fun openConfigFile(project: Project, target: SourceFile) {
    val file = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target.path) }
        ?.takeUnless { it.isDirectory } ?: return
    val offset = target.taskId?.let { taskId ->
        readAction {
            PsiManager.getInstance(project).findFile(file)?.let { taskKeyElement(it, taskId) }?.textOffset
        }
    } ?: 0
    withContext(Dispatchers.EDT) {
        OpenFileDescriptor(project, file, offset).navigate(true)
    }
}
