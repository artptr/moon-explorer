package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.MoonIcons
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.allTasks
import io.github.artptr.moonexplorer.model.loadedTasks
import io.github.artptr.moonexplorer.run.MoonTaskRunner
import java.awt.Insets
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Finds moon tasks in the **All** tab of **Search Everywhere**. When the user selects a task, the contributor runs it.
 * The search examines the same text as the speed search of the tree. Refer to [taskSearchText].
 * The search uses the tasks that [MoonTasksService] loaded. It does not start moon. Thus the search also operates when the **Moon** tool window is closed.
 */
class MoonTaskSearchContributor(private val project: Project) : WeightedSearchEverywhereContributor<MoonTask>, DumbAware {

    /** Runs the target of the selected task. Tests replace this value with a test function, because [MoonTaskRunner.run] starts moon. */
    internal var runTask: (Project, String) -> Unit = MoonTaskRunner::run

    override fun getSearchProviderId(): String = ID

    override fun getGroupName(): String = MoonExplorerBundle.message("search.tasks.group")

    override fun getSortWeight(): Int = SORT_WEIGHT

    override fun showInFindResults(): Boolean = false

    /** Finds the tasks that match [pattern]. The platform calls this function on a background thread. */
    override fun fetchWeightedElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<MoonTask>>,
    ) {
        if (pattern.isBlank()) return
        val tasks = project.service<MoonTasksService>().state.value.loadedTasks ?: return
        // The `*` at the start lets the pattern match in all locations of the text, the same as the speed search of the tree.
        // The matcher ignores the case of the letters.
        val matcher = NameUtil.buildMatcher("*$pattern").build()
        for (task in tasks.allTasks) {
            progressIndicator.checkCanceled()
            val text = taskSearchText(task)
            if (!matcher.matches(text)) continue
            if (!consumer.process(FoundItemDescriptor(task, matcher.matchingDegree(text)))) return
        }
    }

    override fun processSelectedItem(selected: MoonTask, modifiers: Int, searchText: String): Boolean {
        runTask(project, selected.target)
        return true
    }

    private val renderer = TaskRenderer()

    override fun getElementsRenderer(): ListCellRenderer<in MoonTask> = renderer

    /** Shows the target of a task and its description in gray text, the same as the tree. */
    private class TaskRenderer : ColoredListCellRenderer<MoonTask>() {
        init {
            // The list of Search Everywhere sets the left space of each row. Thus the icon of a task is in the same column as the icons of the platform groups.
            ipad = Insets(0, 0, 0, ipad.right)
        }

        override fun customizeCellRenderer(list: JList<out MoonTask>, value: MoonTask?, index: Int, selected: Boolean, hasFocus: Boolean) {
            if (value == null) return
            icon = MoonIcons.Moon
            append(value.target, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            appendTaskDescription(value)
        }
    }

    /** Makes the contributor for the IDE project of the event. */
    class Factory : SearchEverywhereContributorFactory<MoonTask> {
        override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<MoonTask> =
            MoonTaskSearchContributor(requireNotNull(initEvent.project))

        override fun isAvailable(project: Project?): Boolean = project != null
    }

    private companion object {
        const val ID = "MoonExplorer.Tasks"

        /** The position of the group in the **All** tab. A lower value moves the group up. The platform groups use values from 100 to 400. */
        const val SORT_WEIGHT = 500
    }
}
