package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class MoonTaskSearchContributorTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private lateinit var contributor: MoonTaskSearchContributor

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
        contributor = MoonTaskSearchContributor(project)
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun load() {
        service.refresh()
        service.awaitState(TestData.recordedState())
    }

    private fun search(pattern: String): List<FoundItemDescriptor<MoonTask>> {
        val found = mutableListOf<FoundItemDescriptor<MoonTask>>()
        contributor.fetchWeightedElements(pattern, EmptyProgressIndicator()) { found.add(it) }
        return found
    }

    private fun targets(pattern: String): List<String> = search(pattern).map { it.item.target }

    fun testSearchFindsATaskByItsId() {
        load()
        assertSameElements(targets("storybook"), "ui:storybook")
    }

    fun testSearchFindsTheTasksOfAProject() {
        load()
        assertContainsElements(targets("worker"), "worker:audit", "worker:build", "worker:start", "worker:test")
    }

    fun testSearchFindsATaskByItsDescription() {
        load()
        assertSameElements(targets("migrations"), "db:migrate")
    }

    fun testSearchIgnoresTheCase() {
        load()
        assertSameElements(targets("STORYBOOK"), "ui:storybook")
    }

    fun testSearchFindsAFullTarget() {
        load()
        assertContainsElements(targets("web:build"), "web:build")
    }

    fun testSearchFindsNothingForAnUnknownText() {
        load()
        assertEmpty(search("no-such-task"))
    }

    fun testSearchFindsNothingForABlankPattern() {
        load()
        assertEmpty(search("  "))
    }

    fun testSearchFindsNothingWhileTheTasksLoad() {
        assertEquals(TasksState.Loading, service.state.value)
        assertEmpty(search("build"))
    }

    fun testSearchFindsNothingWithoutAWorkspace() {
        service.projectDir = { null }
        service.refresh()
        service.awaitState(TasksState.NoWorkspace)
        assertEmpty(search("build"))
    }

    fun testSearchUsesTheLastTasksAfterAFailedRefresh() {
        load()
        service.queryProjects = { CliResult.Failed("refresh failed") }
        service.refresh()
        service.awaitState(TasksState.Stale(TestData.recordedState(), TasksState.MoonFailed(MoonFailure.Error("refresh failed"))))
        assertSameElements(targets("storybook"), "ui:storybook")
    }

    fun testSearchDoesNotStartMoon() {
        var calls = 0
        service.queryProjects = {
            calls++
            CliResult.Success(TestData.recorded("query-projects.json"))
        }
        load()
        val before = calls
        search("build")
        search("web")
        assertEquals(before, calls)
    }

    fun testSelectionRunsTheTask() {
        load()
        val ran = mutableListOf<Pair<Project, String>>()
        contributor.runTask = { p, target -> ran.add(p to target) }
        val task = search("storybook").single().item
        assertTrue("The popup must close", contributor.processSelectedItem(task, 0, "storybook"))
        assertEquals(listOf(project to "ui:storybook"), ran)
    }

    fun testContributorIsOnlyInTheAllTab() {
        assertFalse(contributor.isShownInSeparateTab)
        assertFalse(contributor.showInFindResults())
    }

    fun testFactoryIsRegistered() {
        assertTrue(SearchEverywhereContributor.EP_NAME.extensionList.any { it is MoonTaskSearchContributor.Factory })
    }
}
