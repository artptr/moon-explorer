package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.settings.MoonSettings

class GroupByActionsTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    override fun setUp() {
        super.setUp()
        project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        } finally {
            super.tearDown()
        }
    }

    private fun event(action: GroupByAction) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
    )

    fun testToolbarHasTheGroupByControlWithThreeModes() {
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.getAction(MoonTasksPanel.TOOLBAR_GROUP_ID) as DefaultActionGroup
        val groupBy = toolbar.childActionsOrStubs.single { actionManager.getId(it) == "MoonExplorer.GroupBy" } as DefaultActionGroup
        assertTrue(groupBy.isPopup)
        assertEquals(
            listOf("MoonExplorer.GroupByProject", "MoonExplorer.GroupByTaskName", "MoonExplorer.GroupByStack"),
            groupBy.childActionsOrStubs.map { actionManager.getId(it) },
        )
    }

    fun testSelectedActionShowsTheCurrentMode() {
        val byProject = GroupByProjectAction()
        val byStack = GroupByStackAction()
        assertTrue(byProject.isSelected(event(byProject)))
        assertFalse(byStack.isSelected(event(byStack)))
    }

    fun testActionChangesTheMode() {
        val action = GroupByTaskNameAction()
        action.setSelected(event(action), true)
        assertEquals(TaskGrouping.TASK_NAME, panel.grouping)
        assertTrue(action.isSelected(event(action)))
    }

    fun testActionIsHiddenWithoutThePanel() {
        val action = GroupByStackAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        assertFalse(event.presentation.isEnabledAndVisible)
    }
}
