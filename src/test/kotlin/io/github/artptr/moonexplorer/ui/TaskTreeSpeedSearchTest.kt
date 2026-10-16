package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.util.TextRange
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.testData.TestData
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.tree.TreePath

class TaskTreeSpeedSearchTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    private val tasks by lazy {
        QueryTasksParser.parse(TestData.recorded("query-tasks.json"))
            .withStacks(QueryProjectsParser.parseStacks(TestData.recorded("query-projects.json")))
    }

    override fun setUp() {
        super.setUp()
        project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
        panel.render(TasksState.Loaded(tasks))
    }

    override fun tearDown() {
        try {
            panel.speedSearch.hidePopup()
            project.service<MoonSettings>().taskGrouping = TaskGrouping.PROJECT
        } finally {
            super.tearDown()
        }
    }

    private val selectedPath: TreePath get() = panel.tree.selectionPath ?: error("No node is selected")

    private val selectedTask: TaskNode get() = selectedPath.lastPathComponent as TaskNode

    fun testSearchSelectsTheFirstNodeThatContainsTheText() {
        panel.speedSearch.findAndSelectElement("typecheck")
        assertEquals("admin:typecheck", selectedTask.task.target)
    }

    fun testSearchFindsATaskInACollapsedProjectNode() {
        val project = selectedPathOf("web")
        assertFalse(panel.tree.isExpanded(project))
        panel.speedSearch.findAndSelectElement("preview")
        assertEquals("web:preview", selectedTask.task.target)
        assertTrue("The search must expand the parent node", panel.tree.isExpanded(selectedPath.parentPath))
    }

    fun testSearchFindsATaskByItsDescription() {
        panel.speedSearch.findAndSelectElement("development server")
        assertEquals("dev", selectedTask.task.id)
    }

    fun testSearchFindsAProjectById() {
        panel.speedSearch.findAndSelectElement("worker")
        assertEquals("worker", (selectedPath.lastPathComponent as ProjectNode).project.id)
    }

    fun testDownAndUpKeysGoToTheNextAndThePreviousMatch() {
        panel.speedSearch.findAndSelectElement("audit")
        assertEquals("api:audit", selectedTask.task.target)
        panel.speedSearch.adjustSelection(KeyEvent.VK_DOWN, "audit")
        assertEquals("db:audit", selectedTask.task.target)
        panel.speedSearch.adjustSelection(KeyEvent.VK_UP, "audit")
        assertEquals("api:audit", selectedTask.task.target)
    }

    fun testSearchOperatesAfterARefresh() {
        panel.render(TasksState.Loading)
        panel.render(TasksState.Loaded(tasks))
        panel.speedSearch.findAndSelectElement("audit")
        assertEquals("api:audit", selectedTask.task.target)
    }

    fun testSearchOperatesInTaskNameGrouping() {
        panel.changeGrouping(TaskGrouping.TASK_NAME)
        panel.speedSearch.findAndSelectElement("typecheck")
        assertEquals("typecheck", selectedPath.lastPathComponent.toString())
        panel.tree.collapsePath(selectedPath)
        // The task nodes in this mode show the project ID.
        panel.speedSearch.findAndSelectElement("worker")
        assertEquals("worker", selectedTask.projectId)
    }

    fun testSearchOperatesInStackGrouping() {
        panel.changeGrouping(TaskGrouping.STACK)
        panel.speedSearch.findAndSelectElement("audit")
        assertEquals("api:audit", selectedTask.task.target)
        assertEquals("backend", selectedPath.parentPath.parentPath.lastPathComponent.toString())
    }

    fun testRendererHighlightsTheFoundText() {
        panel.speedSearch.findAndSelectElement("bundle")
        val node = selectedTask
        assertEquals("build", node.task.id)

        // The popup of the speed search does not open in a headless test. Thus a fake object gives the found text.
        // The fake uses the comparator of the speed search of the panel.
        val tree = Tree()
        val supply = object : SpeedSearchSupply() {
            override fun matchingFragments(text: String): Iterable<TextRange>? =
                panel.speedSearch.comparator.matchingFragments("bundle", text)
            override fun refreshSelection() = Unit
            override fun isPopupActive(): Boolean = true
            override fun getEnteredPrefix(): String = "bundle"
            override fun addChangeListener(listener: PropertyChangeListener) = Unit
            override fun removeChangeListener(listener: PropertyChangeListener) = Unit
            override fun findAndSelectElement(searchQuery: String) = Unit
        }
        supply.installSupplyTo(tree, false)

        val renderer = TaskTreeCellRenderer()
        renderer.getTreeCellRendererComponent(tree, node, true, false, true, 0, false)
        val iterator = renderer.iterator()
        val highlighted = mutableListOf<String>()
        while (iterator.hasNext()) {
            val text = iterator.next()
            if (iterator.textAttributes.style and SimpleTextAttributes.STYLE_SEARCH_MATCH != 0) highlighted += text
        }
        assertEquals(listOf("bundle"), highlighted)
    }

    fun testSpeedSearchIsInstalledOnTheTree() {
        assertSame(panel.speedSearch, SpeedSearchSupply.getSupply(panel.tree, true))
    }

    fun testSearchTextContainsTheIdAndTheDescription() {
        val build = TaskNode(tasks.projects.single { it.id == "web" }.tasks.single { it.id == "build" })
        assertEquals("build  Make the production bundle.", searchText(build))
        assertEquals("web  Make the production bundle.", searchText(TaskNode(build.task, showProject = true)))
    }

    private fun selectedPathOf(projectId: String): TreePath =
        panel.allPaths().single { (it.lastPathComponent as? ProjectNode)?.project?.id == projectId }
}
