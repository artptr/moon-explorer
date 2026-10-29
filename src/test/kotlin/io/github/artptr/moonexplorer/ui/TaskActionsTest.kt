package io.github.artptr.moonexplorer.ui

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.run.MoonRunConfiguration
import io.github.artptr.moonexplorer.run.MoonRunConfigurationType
import io.github.artptr.moonexplorer.run.removeMoonConfigurations

class TaskActionsTest : BasePlatformTestCase() {

    private val task = MoonTask("build", "web:build", "Make the production bundle.")

    override fun tearDown() {
        try {
            removeMoonConfigurations(project)
        } finally {
            super.tearDown()
        }
    }

    private val buildTasks = listOf(
        MoonTask("build", "db:build", null),
        MoonTask("build", "ui:build", null),
        MoonTask("build", "utils:build", null),
    )

    private fun context(
        task: MoonTask?,
        tasks: List<MoonTask>? = null,
        taskNameTasks: List<MoonTask>? = null,
    ) = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(SELECTED_TASK, task)
        .add(SELECTED_TASKS, tasks)
        .add(SELECTED_TASK_NAME_TASKS, taskNameTasks)
        .build()

    /** Runs [action] in [context] and gives the targets and the affected value of each run. */
    private fun runs(action: SelectedTasksRunAction, context: DataContext): List<Pair<List<String>, Boolean>> {
        val runs = mutableListOf<Pair<List<String>, Boolean>>()
        action.run = { _, targets, affected -> runs += targets to affected }
        action.actionPerformed(TestActionEvent.createTestEvent(action, context))
        return runs
    }

    private fun presentation(action: SelectedTasksRunAction, context: DataContext) =
        TestActionEvent.createTestEvent(action, context).also(action::update).presentation

    fun testRunActionShowsTheTarget() {
        val action = RunTaskAction()
        val event = TestActionEvent.createTestEvent(action, context(task))
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals("Run 'web:build'", event.presentation.text)
    }

    fun testRunInProjectsRunsAllTargetsOfTheTaskName() {
        val context = context(null, taskNameTasks = buildTasks)
        val presentation = presentation(RunInProjectsAction(), context)
        assertTrue(presentation.isEnabledAndVisible)
        assertEquals("Run in 3 Projects", presentation.text)
        assertEquals(listOf(listOf("db:build", "ui:build", "utils:build") to false), runs(RunInProjectsAction(), context))
    }

    fun testRunAffectedInProjectsRunsAllTargetsWithAffected() {
        val context = context(null, taskNameTasks = buildTasks)
        assertEquals("Run Affected in 3 Projects", presentation(RunAffectedInProjectsAction(), context).text)
        assertEquals(listOf(listOf("db:build", "ui:build", "utils:build") to true), runs(RunAffectedInProjectsAction(), context))
    }

    fun testRunInProjectsTextForOneProject() {
        assertEquals("Run in 1 Project", presentation(RunInProjectsAction(), context(null, taskNameTasks = buildTasks.take(1))).text)
    }

    fun testRunSelectedTasksRunsOnlyTheSelectedTasks() {
        val selected = listOf(task, MoonTask("audit", "api:audit", null))
        val context = context(null, tasks = selected)
        assertEquals("Run 2 Tasks", presentation(RunSelectedTasksAction(), context).text)
        assertEquals(listOf(listOf("web:build", "api:audit") to false), runs(RunSelectedTasksAction(), context))
    }

    fun testBulkActionsAreHiddenForAnEmptyList() {
        for (action in listOf(RunInProjectsAction(), RunAffectedInProjectsAction(), RunSelectedTasksAction())) {
            val context = context(null, tasks = emptyList(), taskNameTasks = emptyList())
            assertFalse(presentation(action, context).isVisible)
            assertEmpty(runs(action, context))
        }
    }

    fun testBulkActionsAreHiddenWithoutTheirTasks() {
        assertFalse(presentation(RunInProjectsAction(), context(task, tasks = buildTasks)).isVisible)
        assertFalse(presentation(RunAffectedInProjectsAction(), context(task, tasks = buildTasks)).isVisible)
        assertFalse(presentation(RunSelectedTasksAction(), context(task, taskNameTasks = buildTasks)).isVisible)
    }

    fun testActionsAreHiddenWithoutATask() {
        for (action in listOf(RunTaskAction(), EditTaskSettingsAction())) {
            val event = TestActionEvent.createTestEvent(action, context(null))
            action.update(event)
            assertFalse(event.presentation.isVisible)
        }
    }

    fun testEditSettingsActionShowsTheTarget() {
        val action = EditTaskSettingsAction()
        val event = TestActionEvent.createTestEvent(action, context(task))
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals("Edit 'web:build' Settings...", event.presentation.text)
    }

    fun testActionTextsKeepUnderscoresInTheTarget() {
        val task = MoonTask("build_test", "web:build_test", null)
        val presentations = listOf(RunTaskAction(), EditTaskSettingsAction()).map { action ->
            TestActionEvent.createTestEvent(action, context(task)).also(action::update).presentation
        }
        assertEquals(listOf("Run 'web:build_test'", "Edit 'web:build_test' Settings..."), presentations.map { it.text })
        assertEquals(listOf(0, 0), presentations.map { it.mnemonic })
    }

    fun testEditSettingsActionOpensTheConfigurationOfTheTask() {
        val action = EditTaskSettingsAction()
        val edited = mutableListOf<String>()
        action.edit = { _, settings, title ->
            assertEquals("Edit Configuration", title)
            edited += (settings.configuration as MoonRunConfiguration).target
            true
        }
        action.actionPerformed(TestActionEvent.createTestEvent(action, context(task)))
        assertEquals(listOf("web:build"), edited)
        val saved = RunManager.getInstance(project).getConfigurationSettingsList(MoonRunConfigurationType.getInstance()).single()
        assertFalse(saved.isTemporary)
    }
}
