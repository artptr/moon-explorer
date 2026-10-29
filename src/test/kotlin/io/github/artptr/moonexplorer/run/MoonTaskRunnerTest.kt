package io.github.artptr.moonexplorer.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MoonTaskRunnerTest : BasePlatformTestCase() {

    private val runManager get() = RunManager.getInstance(project)

    override fun tearDown() {
        try {
            removeMoonConfigurations(project)
        } finally {
            super.tearDown()
        }
    }

    private fun moonConfigurations() = runManager.getConfigurationSettingsList(MoonRunConfigurationType.getInstance())

    fun testTemporaryConfigurationForATask() {
        val settings = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true)
        assertTrue(settings.isTemporary)
        assertEquals("web:build", settings.name)
        assertEquals("web:build", (settings.configuration as MoonRunConfiguration).target)
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testExistingConfigurationIsUsedAgain() {
        val first = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true)
        val second = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true)
        assertSame(first, second)
        assertEquals(1, moonConfigurations().size)
    }

    fun testTemporaryConfigurationForSeveralTargets() {
        val settings = MoonTaskRunner.findOrCreate(project, listOf("web:build", "api:build"), affected = true, temporary = true)
        assertTrue(settings.isTemporary)
        assertEquals("build (web, api), affected", settings.name)
        val configuration = settings.configuration as MoonRunConfiguration
        assertEquals(listOf("web:build", "api:build"), configuration.targets)
        assertTrue(configuration.affected)
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testConfigurationForSeveralTargetsIsUsedAgainOnlyWithTheSameAffectedValue() {
        val targets = listOf("web:build", "api:build")
        val all = MoonTaskRunner.findOrCreate(project, targets, affected = false, temporary = true)
        assertSame(all, MoonTaskRunner.findOrCreate(project, targets, affected = false, temporary = true))
        val affected = MoonTaskRunner.findOrCreate(project, targets, affected = true, temporary = true)
        assertNotSame(all, affected)
        assertNotSame(all, MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true))
        assertEquals(3, moonConfigurations().size)
    }

    fun testSavedConfigurationIsUsedBeforeATemporaryConfiguration() {
        val temporary = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true)
        val saved = runManager.createConfiguration("web:build (saved)", MoonRunConfigurationType.getInstance().factory)
        (saved.configuration as MoonRunConfiguration).targets = listOf("web:build")
        runManager.addConfiguration(saved)
        assertNotSame(temporary, saved)
        assertSame(saved, MoonTaskRunner.find(project, listOf("web:build"), affected = false))
        assertSame(saved, MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false, temporary = true))
    }

    /** Opens the dialog for [target] with a test function that gives [ok]. Gives the configurations that the dialog got. */
    private fun edit(target: String, ok: Boolean): List<RunnerAndConfigurationSettings> {
        val edited = mutableListOf<RunnerAndConfigurationSettings>()
        val result = MoonTaskRunner.editConfiguration(project, target) { _, settings, _ ->
            edited += settings
            ok
        }
        assertEquals(ok, result)
        return edited
    }

    fun testEditOpensTheSavedConfiguration() {
        val saved = MoonTaskRunner.findOrCreate(project, listOf("api:audit"), affected = false, temporary = false)
        assertEquals(listOf(saved), edit("api:audit", ok = true))
        assertEquals(listOf(saved), moonConfigurations())
        assertFalse(saved.isTemporary)
    }

    fun testEditSavesTheTemporaryConfigurationOnOk() {
        val temporary = MoonTaskRunner.findOrCreate(project, listOf("api:audit"), affected = false, temporary = true)
        assertEquals(listOf(temporary), edit("api:audit", ok = true))
        assertEquals(listOf(temporary), moonConfigurations())
        assertFalse(temporary.isTemporary)
    }

    fun testEditKeepsTheTemporaryConfigurationOnCancel() {
        val temporary = MoonTaskRunner.findOrCreate(project, listOf("api:audit"), affected = false, temporary = true)
        edit("api:audit", ok = false)
        assertTrue(temporary.isTemporary)
    }

    fun testEditSavesANewConfigurationOnOk() {
        val edited = edit("web:build", ok = true).single()
        assertEquals(listOf(edited), moonConfigurations())
        assertFalse(edited.isTemporary)
        assertEquals("web:build", edited.name)
        assertEquals(listOf("web:build"), (edited.configuration as MoonRunConfiguration).targets)
        assertEquals(edited, runManager.selectedConfiguration)
    }

    fun testEditDoesNotAddANewConfigurationOnCancel() {
        val edited = edit("web:build", ok = false).single()
        assertEquals(listOf("web:build"), (edited.configuration as MoonRunConfiguration).targets)
        assertEmpty(moonConfigurations())
    }
}
