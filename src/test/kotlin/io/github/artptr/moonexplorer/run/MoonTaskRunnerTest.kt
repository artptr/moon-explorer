package io.github.artptr.moonexplorer.run

import com.intellij.execution.RunManager
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
        val settings = MoonTaskRunner.findOrCreate(project, "web:build", temporary = true)
        assertTrue(settings.isTemporary)
        assertEquals("web:build", settings.name)
        assertEquals("web:build", (settings.configuration as MoonRunConfiguration).target)
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testExistingConfigurationIsUsedAgain() {
        val first = MoonTaskRunner.findOrCreate(project, "web:build", temporary = true)
        val second = MoonTaskRunner.findOrCreate(project, "web:build", temporary = true)
        assertSame(first, second)
        assertEquals(1, moonConfigurations().size)
    }

    fun testTemporaryConfigurationForSeveralTargets() {
        val settings = MoonTaskRunner.findOrCreate(project, listOf("web:build", "api:build"), affected = true, temporary = true)
        assertTrue(settings.isTemporary)
        assertEquals("build (2 projects), affected", settings.name)
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

    fun testCreateConfigurationSavesIt() {
        val settings = MoonTaskRunner.createConfiguration(project, "api:audit")
        assertFalse(settings.isTemporary)
        assertEquals(listOf(settings), moonConfigurations())
        assertEquals(settings, runManager.selectedConfiguration)
    }

    fun testCreateConfigurationSavesTheTemporaryConfiguration() {
        val temporary = MoonTaskRunner.findOrCreate(project, "api:audit", temporary = true)
        val saved = MoonTaskRunner.createConfiguration(project, "api:audit")
        assertSame(temporary, saved)
        assertFalse(saved.isTemporary)
    }
}
