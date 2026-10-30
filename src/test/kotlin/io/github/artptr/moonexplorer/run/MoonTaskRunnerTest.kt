package io.github.artptr.moonexplorer.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle

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

    /** Adds a saved configuration with [name] that runs [targets] with [affected]. */
    private fun addSaved(name: String, targets: List<String>, affected: Boolean = false): RunnerAndConfigurationSettings =
        runManager.createConfiguration(name, MoonRunConfigurationType.getInstance().factory).also {
            (it.configuration as MoonRunConfiguration).setUp(targets, affected)
            it.name = name
            runManager.addConfiguration(it)
        }

    fun testTemporaryConfigurationForATask() {
        val settings = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false)
        assertTrue(settings.isTemporary)
        assertEquals("web:build", settings.name)
        assertEquals("web:build", (settings.configuration as MoonRunConfiguration).target)
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testExistingConfigurationIsUsedAgain() {
        val first = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false)
        val second = MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false)
        assertSame(first, second)
        assertEquals(1, moonConfigurations().size)
    }

    fun testTemporaryConfigurationForSeveralTargets() {
        val settings = MoonTaskRunner.findOrCreate(project, listOf("web:build", "api:build"), affected = true)
        assertTrue(settings.isTemporary)
        assertEquals("build (web, api), affected", settings.name)
        val configuration = settings.configuration as MoonRunConfiguration
        assertEquals(listOf("web:build", "api:build"), configuration.targets)
        assertTrue(configuration.affected)
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testConfigurationForSeveralTargetsIsUsedAgainOnlyWithTheSameAffectedValue() {
        val targets = listOf("web:build", "api:build")
        val all = MoonTaskRunner.findOrCreate(project, targets, affected = false)
        assertSame(all, MoonTaskRunner.findOrCreate(project, targets, affected = false))
        val affected = MoonTaskRunner.findOrCreate(project, targets, affected = true)
        assertNotSame(all, affected)
        assertNotSame(all, MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false))
        assertEquals(3, moonConfigurations().size)
    }

    fun testSavedConfigurationIsUsedBeforeATemporaryConfiguration() {
        MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false)
        val saved = addSaved("web:build (saved)", listOf("web:build"))
        assertSame(saved, MoonTaskRunner.find(project, listOf("web:build"), affected = false))
        assertSame(saved, MoonTaskRunner.findOrCreate(project, listOf("web:build"), affected = false))
    }

    fun testFirstSavedConfigurationIsUsed() {
        val first = addSaved("web:build", listOf("web:build"))
        addSaved("web:build (second)", listOf("web:build"))
        assertSame(first, MoonTaskRunner.find(project, listOf("web:build"), affected = false))
    }

    /** Opens the dialog for [target] with a test function that gives [ok]. Gives the configurations that the dialog got. */
    private fun edit(target: String, ok: Boolean): List<RunnerAndConfigurationSettings> {
        val edited = mutableListOf<RunnerAndConfigurationSettings>()
        MoonTaskRunner.editConfiguration(project, target) { _, settings, title ->
            assertEquals(MoonExplorerBundle.message("run.configuration.edit.title"), title)
            edited += settings
            ok
        }
        return edited
    }

    fun testEditOpensTheSavedConfiguration() {
        val saved = addSaved("api:audit", listOf("api:audit"))
        assertEquals(listOf(saved), edit("api:audit", ok = true))
        assertEquals(listOf(saved), moonConfigurations())
        assertFalse(saved.isTemporary)
        assertEquals(saved, runManager.selectedConfiguration)
    }

    fun testEditKeepsTheSavedConfigurationOnCancel() {
        val saved = addSaved("api:audit", listOf("api:audit"))
        val other = addSaved("web:build", listOf("web:build"))
        runManager.selectedConfiguration = other
        assertEquals(listOf(saved), edit("api:audit", ok = false))
        assertEquals(other, runManager.selectedConfiguration)
        assertEquals(setOf(saved, other), moonConfigurations().toSet())
        assertFalse(saved.isTemporary)
    }

    fun testEditDoesNotOpenAnAffectedConfiguration() {
        val affected = addSaved("web:build, affected", listOf("web:build"), affected = true)
        val edited = edit("web:build", ok = true).single()
        assertNotSame(affected, edited)
        assertFalse((edited.configuration as MoonRunConfiguration).affected)
        assertEquals(setOf(affected, edited), moonConfigurations().toSet())
    }

    fun testEditSavesTheTemporaryConfigurationOnOk() {
        val temporary = MoonTaskRunner.findOrCreate(project, listOf("api:audit"), affected = false)
        assertEquals(listOf(temporary), edit("api:audit", ok = true))
        assertEquals(listOf(temporary), moonConfigurations())
        assertFalse(temporary.isTemporary)
        assertEquals(temporary, runManager.selectedConfiguration)
    }

    fun testEditKeepsTheTemporaryConfigurationOnCancel() {
        val temporary = MoonTaskRunner.findOrCreate(project, listOf("api:audit"), affected = false)
        assertEquals(listOf(temporary), edit("api:audit", ok = false))
        assertEquals(listOf(temporary), moonConfigurations())
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
