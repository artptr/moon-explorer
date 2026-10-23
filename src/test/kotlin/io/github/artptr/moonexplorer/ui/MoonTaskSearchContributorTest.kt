package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.loadStale
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
        service.loadRecorded()
        contributor = MoonTaskSearchContributor(project)
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun search(pattern: String): List<FoundItemDescriptor<MoonTask>> {
        val found = mutableListOf<FoundItemDescriptor<MoonTask>>()
        contributor.fetchWeightedElements(pattern, EmptyProgressIndicator()) { found.add(it) }
        return found
    }

    private fun targets(pattern: String): List<String> = search(pattern).map { it.item.target }

    fun testSearchFindsATaskByItsId() {
        assertSameElements(targets("storybook"), "ui:storybook")
    }

    fun testSearchFindsTheTasksOfAProject() {
        assertContainsElements(targets("worker"), "worker:audit", "worker:build", "worker:start", "worker:test")
    }

    fun testSearchFindsATaskByItsDescription() {
        assertSameElements(targets("migrations"), "db:migrate")
    }

    fun testSearchFindsTwoWordsOfTheDescription() {
        assertSameElements(targets("database migrations"), "db:migrate")
    }

    fun testSearchIgnoresTheCase() {
        assertSameElements(targets("STORYBOOK"), "ui:storybook")
    }

    fun testSearchFindsAFullTarget() {
        assertContainsElements(targets("web:build"), "web:build")
    }

    fun testSearchFindsNothingForAnUnknownText() {
        assertEmpty(search("no-such-task"))
    }

    fun testSearchFindsNothingForABlankPattern() {
        assertEmpty(search("  "))
    }

    fun testSearchFindsNothingWhileTheTasksLoad() {
        runBlocking { service.resetForTests() }
        assertEquals(TasksState.Loading, service.state.value)
        assertEmpty(search("build"))
    }

    fun testSearchFindsNothingWithoutAWorkspace() {
        service.loadNoWorkspace()
        assertEmpty(search("build"))
    }

    fun testSearchUsesTheLastTasksAfterAFailedRefresh() {
        service.loadStale()
        assertSameElements(targets("storybook"), "ui:storybook")
    }

    fun testSearchDoesNotStartMoon() {
        var calls = 0
        service.queryProjects = {
            calls++
            CliResult.Success(TestData.recorded("query-projects.json"))
        }
        search("build")
        search("web")
        assertEquals(0, calls)
    }

    fun testSelectionRunsTheTask() {
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

    fun testRendererHasNoLeftSpace() {
        val task = search("storybook").single().item
        val component = contributor.elementsRenderer.getListCellRendererComponent(JBList(task), task, 0, false, false)
        val renderer = component as ColoredListCellRenderer<*>
        assertEquals("ui:storybook", renderer.getCharSequence(true).toString())
        assertEquals(0, renderer.ipad.left)
    }

    fun testFactoryIsRegistered() {
        assertTrue(SearchEverywhereContributor.EP_NAME.extensionList.any { it is MoonTaskSearchContributor.Factory })
    }
}
