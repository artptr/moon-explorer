package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.settings.MoonSettings

class GroupByActionsTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        project.service<MoonSettings>().taskCategory = TaskCategory.NONE
        project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().taskCategory = TaskCategory.NONE
        project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        } finally {
            super.tearDown()
        }
    }

    private fun event(action: TasksPanelToggleAction) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
    )

    fun testGroupByControlHasTheCategoryAndTheMainGroupWithASeparator() {
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.getAction(MoonTasksPanel.TOOLBAR_GROUP_ID) as DefaultActionGroup
        val groupBy = toolbar.childActionsOrStubs.single { actionManager.getId(it) == "MoonExplorer.GroupBy" } as DefaultActionGroup
        assertTrue(groupBy.isPopup)
        assertEquals(
            listOf(
                "MoonExplorer.CategoryNone",
                "MoonExplorer.CategoryStack",
                "MoonExplorer.CategoryLayer",
                "separator",
                "MoonExplorer.GroupByProject",
                "MoonExplorer.GroupByTaskName",
            ),
            groupBy.childActionsOrStubs.map { if (it is Separator) "separator" else actionManager.getId(it) },
        )
    }

    fun testSelectedActionsShowTheCurrentValues() {
        assertTrue(GroupByProjectAction().let { it.isSelected(event(it)) })
        assertFalse(GroupByTaskNameAction().let { it.isSelected(event(it)) })
        assertTrue(CategoryNoneAction().let { it.isSelected(event(it)) })
        assertFalse(CategoryStackAction().let { it.isSelected(event(it)) })
        assertFalse(CategoryLayerAction().let { it.isSelected(event(it)) })
    }

    fun testGroupByActionChangesOnlyTheMainGroup() {
        panel.changeCategory(TaskCategory.STACK)
        val action = GroupByTaskNameAction()
        action.setSelected(event(action), true)
        assertEquals(TaskGrouping.TASK_NAME, panel.grouping)
        assertEquals(TaskCategory.STACK, panel.category)
        assertTrue(action.isSelected(event(action)))
    }

    fun testCategoryActionChangesOnlyTheCategory() {
        panel.changeGrouping(TaskGrouping.TASK_NAME)
        val action = CategoryLayerAction()
        action.setSelected(event(action), true)
        assertEquals(TaskCategory.LAYER, panel.category)
        assertEquals(TaskGrouping.TASK_NAME, panel.grouping)
        assertTrue(action.isSelected(event(action)))
        assertFalse(CategoryNoneAction().let { it.isSelected(event(it)) })
    }

    fun testActionsAreHiddenWithoutThePanel() {
        for (action in listOf(GroupByProjectAction(), CategoryStackAction())) {
            val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
            action.update(event)
            assertFalse(event.presentation.isEnabledAndVisible)
        }
    }
}
