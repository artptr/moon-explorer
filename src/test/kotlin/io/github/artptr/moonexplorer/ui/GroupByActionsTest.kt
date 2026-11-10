package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping

class GroupByActionsTest : MoonTasksPanelTestCase() {

    private fun event(action: TasksPanelToggleAction) = tasksPanelEvent(action, project, panel)

    private fun selected(action: TasksPanelToggleAction) = action.isSelected(event(action))

    fun testGroupByControlHasTheMainGroupAndTheCategoryWithASeparator() {
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.getAction(MoonTasksPanel.TOOLBAR_GROUP_ID) as DefaultActionGroup
        val groupBy = toolbar.childActionsOrStubs.single { actionManager.getId(it) == "MoonExplorer.GroupBy" } as DefaultActionGroup
        assertTrue(groupBy.isPopup)
        assertEquals(
            listOf(
                "MoonExplorer.GroupByProject",
                "MoonExplorer.GroupByTaskName",
                "separator",
                "MoonExplorer.CategoryNone",
                "MoonExplorer.CategoryStack",
                "MoonExplorer.CategoryLayer",
                "MoonExplorer.CategoryTag",
            ),
            groupBy.childActionsOrStubs.map { if (it is Separator) "separator" else actionManager.getId(it) },
        )
    }

    fun testSelectedActionsShowTheCurrentValues() {
        assertTrue(selected(GroupByProjectAction()))
        assertFalse(selected(GroupByTaskNameAction()))
        assertTrue(selected(CategoryNoneAction()))
        assertFalse(selected(CategoryStackAction()))
        assertFalse(selected(CategoryLayerAction()))
        assertFalse(selected(CategoryTagAction()))
    }

    fun testGroupByActionChangesOnlyTheMainGroup() {
        panel.changeCategory(TaskCategory.STACK)
        val action = GroupByTaskNameAction()
        action.setSelected(event(action), true)
        assertEquals(TaskGrouping.TASK_NAME, panel.mainGroup)
        assertEquals(TaskCategory.STACK, panel.category)
        assertTrue(selected(action))
    }

    fun testCategoryActionChangesOnlyTheCategory() {
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        val action = CategoryLayerAction()
        action.setSelected(event(action), true)
        assertEquals(TaskCategory.LAYER, panel.category)
        assertEquals(TaskGrouping.TASK_NAME, panel.mainGroup)
        assertTrue(selected(action))
        assertFalse(selected(CategoryNoneAction()))
    }

    fun testActionsAreHiddenWithoutThePanel() {
        for (action in listOf(GroupByProjectAction(), CategoryStackAction())) {
            assertFalse(action.updatedPresentation(SimpleDataContext.getProjectContext(project)).isEnabledAndVisible)
        }
    }
}
