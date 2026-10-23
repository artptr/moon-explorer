package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class MoonRunAnythingProviderTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private lateinit var provider: MoonRunAnythingProvider
    private val context by lazy { SimpleDataContext.getProjectContext(project) }

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
        provider = MoonRunAnythingProvider()
        service.refresh()
        service.awaitState(TestData.recordedState())
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    fun testTargetOfRunCommand() {
        assertEquals("web:build", MoonRunAnythingProvider.targetOf(listOf("run", "web:build")))
        assertEquals(":build", MoonRunAnythingProvider.targetOf(listOf("run", ":build")))
    }

    fun testNoTargetForOtherParameters() {
        assertNull(MoonRunAnythingProvider.targetOf(emptyList()))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("query", "projects")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "web:build", "web:lint")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "web:build", "--", "--watch")))
    }

    fun testCompletionAfterMoonGivesRun() {
        assertContainsElements(provider.getValues(context, "moon "), "moon run")
    }

    fun testCompletionAfterMoonRunGivesTheLoadedTargets() {
        val values = provider.getValues(context, "moon run ")
        val expected = TestData.recordedTasks().projects.flatMap { it.tasks }.map { "moon run ${it.target}" }
        assertSameElements(values, expected)
    }

    fun testNoCompletionAfterATarget() {
        assertEmpty(provider.getValues(context, "moon run web:build "))
    }

    fun testProviderMatchesOnlyMoonRunWithATarget() {
        assertEquals("moon run web:build", provider.findMatchingValue(context, "moon run web:build"))
        assertNull(provider.findMatchingValue(context, "moon run"))
        assertNull(provider.findMatchingValue(context, "moon query projects"))
        assertNull(provider.findMatchingValue(context, "moon run web:build web:lint"))
        assertNull(provider.findMatchingValue(context, "npm run build"))
        assertNull(provider.findMatchingValue(context, "moonx run web:build"))
    }

    fun testRunRunsTheTarget() {
        val ran = mutableListOf<Pair<Project, String>>()
        provider.runTask = { p, target -> ran.add(p to target) }
        provider.execute(context, "moon run web:build")
        assertEquals(listOf(project to "web:build"), ran)
    }

    fun testProviderIsRegistered() {
        assertTrue(RunAnythingProvider.EP_NAME.extensionList.any { it is MoonRunAnythingProvider })
    }
}
