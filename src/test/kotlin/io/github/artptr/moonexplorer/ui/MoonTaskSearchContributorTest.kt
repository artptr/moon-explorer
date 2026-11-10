package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import io.github.artptr.moonexplorer.model.*
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class MoonTaskSearchContributorTest : MoonTasksServiceTestCase() {

    private lateinit var contributor: MoonTaskSearchContributor

    override fun setUp() {
        super.setUp()
        service.loadRecorded()
        contributor = MoonTaskSearchContributor(project)
    }

    private fun search(pattern: String): List<FoundItemDescriptor<MoonTask>> {
        val found = mutableListOf<FoundItemDescriptor<MoonTask>>()
        contributor.fetchWeightedElements(pattern, EmptyProgressIndicator()) { found.add(it) }
        return found
    }

    private fun targets(pattern: String): List<String> = search(pattern).map { it.item.target }

    fun testSearchFindsTasksByIdProjectDescriptionAndTarget() {
        // Each pattern and the targets that the search must find. The search can also find other targets for the project and the target.
        assertSameElements(targets("storybook"), "ui:storybook")
        assertSameElements(targets("STORYBOOK"), "ui:storybook")
        assertSameElements(targets("migrations"), "db:migrate")
        assertSameElements(targets("database migrations"), "db:migrate")
        assertContainsElements(targets("worker"), "worker:audit", "worker:build", "worker:check", "worker:start")
        assertContainsElements(targets("web:build"), "web:build")
    }

    fun testSearchFindsNothingForAnUnknownTextOrABlankPattern() {
        assertEmpty(search("no-such-task"))
        assertEmpty(search("  "))
    }

    fun testSearchFindsNothingDuringTheFirstLoad() {
        runBlocking { service.resetForTests() }
        assertEquals(TasksState.Loading(null), service.state.value)
        assertEmpty(search("build"))
    }

    fun testSearchUsesTheLastTasksDuringARefresh() {
        // When you save a configuration file, the plugin starts a refresh. The search must show the results during the refresh.
        assertEquals(TasksState.Loading(TestData.recordedState()), service.startEndlessRefresh())
        assertSameElements(targets("storybook"), "ui:storybook")
    }

    fun testSearchFindsNothingWithoutAWorkspace() {
        service.loadNoWorkspace()
        assertEmpty(search("build"))
    }

    fun testSelectionRunsTheTask() {
        val ran = mutableListOf<Pair<Project, String>>()
        contributor.runTask = { p, target -> ran.add(p to target) }
        val task = search("storybook").single().item
        assertTrue("The popup must close", contributor.processSelectedItem(task, 0, "storybook"))
        assertEquals(listOf(project to "ui:storybook"), ran)
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
