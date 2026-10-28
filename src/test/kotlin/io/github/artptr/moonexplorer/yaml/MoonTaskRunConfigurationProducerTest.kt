package io.github.artptr.moonexplorer.yaml

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.run.MoonRunConfiguration
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class MoonTaskRunConfigurationProducerTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    private val producer get() = RunConfigurationProducer.getInstance(MoonTaskRunConfigurationProducer::class.java)

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
        service.loadRecorded()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    /** Gives the leaf of the task key [taskId] in the file [path] of the test workspace. */
    private fun taskKey(path: String, taskId: String): PsiElement {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(TestData.workspaceRoot.resolve(path)) ?: error("No file $path")
        val psi = PsiManager.getInstance(project).findFile(file) ?: error("No PSI for $path")
        return PsiTreeUtil.collectElements(psi) { it.firstChild == null }.single { taskKeyAt(it)?.taskId == taskId }
    }

    private fun context(element: PsiElement): ConfigurationContext = ConfigurationContext.getFromContext(
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.PSI_ELEMENT, element)
            .add(Location.DATA_KEY, PsiLocation(element))
            .add(LangDataKeys.MODULE, module)
            .build(),
        "test",
    )

    fun testTaskKeyOfAProjectMakesAConfigurationForTheTarget() {
        val context = context(taskKey("apps/web/moon.yml", "build"))
        val created = producer.createConfigurationFromContext(context) ?: error("No configuration")
        val configuration = created.configuration as MoonRunConfiguration
        assertEquals(listOf("web:build"), configuration.targets)
        assertFalse(configuration.affected)
        assertEquals("web:build", configuration.name)
        assertTrue(producer.isConfigurationFromContext(configuration, context))
    }

    fun testConfigurationWithOtherTargetsOrAffectedIsNotFromTheContext() {
        val context = context(taskKey("apps/web/moon.yml", "build"))
        val configuration = producer.createConfigurationFromContext(context)!!.configuration as MoonRunConfiguration
        configuration.affected = true
        assertFalse(producer.isConfigurationFromContext(configuration, context))
        configuration.affected = false
        configuration.targets = listOf("web:build", "api:build")
        assertFalse(producer.isConfigurationFromContext(configuration, context))
    }

    fun testSharedTaskKeyMakesNoConfiguration() {
        assertNull(producer.createConfigurationFromContext(context(taskKey(".moon/tasks/frontend.yml", "test"))))
    }
}
