package io.github.artptr.moonexplorer.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.ID_ORDER
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.testData.TestData
import javax.swing.tree.DefaultMutableTreeNode

class TaskTreeModelTest : BasePlatformTestCase() {

    private val tasks by lazy { TestData.recordedTasks() }

    private val root by lazy { buildTaskTree(tasks) }

    private fun DefaultMutableTreeNode.childNodes() = children().toList().map { it as DefaultMutableTreeNode }

    private fun task(project: String, task: String): DefaultMutableTreeNode =
        root.childNodes().single { (it.userObject as MoonProject).id == project }
            .childNodes().single { (it.userObject as MoonTask).id == task }

    fun testTopNodesAreProjectsInOrder() {
        val ids = root.childNodes().map { (it.userObject as MoonProject).id }
        assertEquals(listOf("admin", "api", "db", "ui", "utils", "web", "worker"), ids)
    }

    fun testProjectNodesContainTasksInOrder() {
        val web = root.childNodes().single { (it.userObject as MoonProject).id == "web" }
        val ids = web.childNodes().map { (it.userObject as MoonTask).id }
        assertEquals(listOf("build", "dev", "format", "lint", "preview", "test", "typecheck"), ids)
        assertTrue(web.childNodes().all { it.isLeaf })
    }

    fun testTaskNodeShowsDescriptionInGray() {
        val fragments = render(task("web", "build"))
        assertEquals("build", fragments[0].first)
        assertEquals("Make the production bundle.", fragments[1].first.trim())
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, fragments[1].second)
    }

    fun testTaskNodeWithoutDescriptionShowsOnlyId() {
        assertEquals(listOf("format"), render(task("web", "format")).map { it.first })
    }

    fun testGroupByTaskNameHasANodeForEachTaskName() {
        val byName = buildTaskTree(tasks, TaskGrouping.TASK_NAME)
        val names = byName.childNodes().map { (it as TaskNameNode).name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
        assertTrue(names.containsAll(listOf("audit", "build", "lint", "typecheck")))
        assertEquals(names.size, names.toSet().size)
    }

    fun testTaskNameNodeContainsTheProjectsWithTheTask() {
        val byName = buildTaskTree(tasks, TaskGrouping.TASK_NAME)
        val lint = byName.childNodes().single { (it as TaskNameNode).name == "lint" }
        val children = lint.childNodes().map { it as TaskNode }
        assertEquals(listOf("admin", "api", "db", "ui", "utils", "web", "worker"), children.map { it.projectId })
        assertTrue(children.all { it.task.id == "lint" && it.showProject && it.isLeaf })
        val audit = byName.childNodes().single { (it as TaskNameNode).name == "audit" }
        assertEquals(listOf("api", "db", "worker"), audit.childNodes().map { (it as TaskNode).projectId })
    }

    fun testTaskNodeInTaskNameGroupShowsTheProject() {
        val byName = buildTaskTree(tasks, TaskGrouping.TASK_NAME)
        val build = byName.childNodes().single { (it as TaskNameNode).name == "build" }
        val web = build.childNodes().single { (it as TaskNode).projectId == "web" }
        assertEquals("web", render(web)[0].first)
    }

    fun testGroupByStackHasStackNodesWithProjects() {
        val byStack = buildTaskTree(tasks, TaskGrouping.STACK)
        assertEquals(listOf("backend", "frontend"), byStack.childNodes().map { (it as StackNode).stack })
        val backend = byStack.childNodes().first()
        assertEquals(listOf("api", "db", "worker"), backend.childNodes().map { (it as ProjectNode).project.id })
        val api = backend.childNodes().first()
        assertTrue(api.childNodes().all { it is TaskNode })
        assertTrue(api.childNodes().isNotEmpty())
    }

    fun testProjectsWithoutStackAreInTheNoneNode() {
        // The project config has no stack value. It also has no tasks. Thus give it a task.
        val config = MoonProject("config", listOf(MoonTask("check", "config:check", null)), stack = null)
        val withConfig = WorkspaceTasks(tasks.projects + config)
        val byStack = buildTaskTree(withConfig, TaskGrouping.STACK)
        val none = byStack.childNodes().last() as StackNode
        assertNull(none.stack)
        assertEquals(MoonExplorerBundle.message("tasks.tree.noStack"), none.toString())
        assertEquals(listOf("config"), none.childNodes().map { (it as ProjectNode).project.id })
    }

    fun testNoNoneNodeIfAllProjectsHaveAStack() {
        val byStack = buildTaskTree(tasks, TaskGrouping.STACK)
        assertTrue(byStack.childNodes().all { (it as StackNode).stack != null })
    }

    private fun render(node: DefaultMutableTreeNode): List<Pair<String, SimpleTextAttributes>> {
        val renderer = TaskTreeCellRenderer()
        renderer.getTreeCellRendererComponent(Tree(), node, false, false, true, 0, false)
        val iterator = renderer.iterator()
        val fragments = mutableListOf<Pair<String, SimpleTextAttributes>>()
        while (iterator.hasNext()) {
            val text = iterator.next()
            fragments += text to iterator.textAttributes
        }
        return fragments
    }

    fun testOtherNodeShowsItsText() {
        val fragments = render(DefaultMutableTreeNode("other"))
        assertEquals(listOf("other"), fragments.map { it.first })
    }

    fun testIdOrderIgnoresCaseAndIsStable() {
        assertEquals(listOf("alpha", "Beta", "beta", "zeta"), listOf("zeta", "beta", "alpha", "Beta").sortedWith(ID_ORDER))
    }
}
