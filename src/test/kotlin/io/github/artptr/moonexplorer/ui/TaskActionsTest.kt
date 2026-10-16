package io.github.artptr.moonexplorer.ui

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.CommonDataKeys
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

    private fun context(task: MoonTask?) = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(SELECTED_TASK, task)
        .build()

    fun testRunActionShowsTheTarget() {
        val action = RunTaskAction()
        val event = TestActionEvent.createTestEvent(action, context(task))
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals("Run 'web:build'", event.presentation.text)
    }

    fun testActionsAreHiddenWithoutATask() {
        for (action in listOf(RunTaskAction(), CreateRunConfigurationAction())) {
            val event = TestActionEvent.createTestEvent(action, context(null))
            action.update(event)
            assertFalse(event.presentation.isVisible)
        }
    }

    fun testCreateRunConfigurationActionSavesAConfiguration() {
        val action = CreateRunConfigurationAction()
        action.actionPerformed(TestActionEvent.createTestEvent(action, context(task)))
        val saved = RunManager.getInstance(project).getConfigurationSettingsList(MoonRunConfigurationType.getInstance()).single()
        assertFalse(saved.isTemporary)
        assertEquals("web:build", (saved.configuration as MoonRunConfiguration).target)
    }
}
