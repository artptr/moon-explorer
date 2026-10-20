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
import io.github.artptr.moonexplorer.settings.resetForTests

class GroupByActionsTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        project.service<MoonSettings>().resetForTests()
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().resetForTests()
        } finally {
            super.tearDown()
        }
    }

    private fun event(action: TasksPanelToggleAction) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
    )

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
            val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
            action.update(event)
            assertFalse(event.presentation.isEnabledAndVisible)
        }
    }
}
