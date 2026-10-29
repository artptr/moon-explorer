package io.github.artptr.moonexplorer.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.ID_ORDER
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.TaskCategory
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
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), ids)
    }

    fun testProjectNodesContainTasksInOrder() {
        val web = root.childNodes().single { (it.userObject as MoonProject).id == "web" }
        val ids = web.childNodes().map { (it.userObject as MoonTask).id }
        assertEquals(listOf("build", "dev", "format", "lint", "preview", "test", "tsc"), ids)
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
        val byName = buildTaskTree(tasks, grouping = TaskGrouping.TASK_NAME)
        val names = byName.childNodes().map { (it as TaskNameNode).name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
        assertTrue(names.containsAll(listOf("audit", "build", "lint", "typecheck")))
        assertEquals(names.size, names.toSet().size)
    }

    fun testTaskNameNodeContainsTheProjectsWithTheTask() {
        val byName = buildTaskTree(tasks, grouping = TaskGrouping.TASK_NAME)
        val lint = byName.childNodes().single { (it as TaskNameNode).name == "lint" }
        val children = lint.childNodes().map { it as TaskNode }
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), children.map { it.projectId })
        assertTrue(children.all { it.task.id == "lint" && it.showProject && it.isLeaf })
        val audit = byName.childNodes().single { (it as TaskNameNode).name == "audit" }
        assertEquals(listOf("api", "db", "worker"), audit.childNodes().map { (it as TaskNode).projectId })
    }

    fun testTaskNodeInTaskNameGroupShowsTheProject() {
        val byName = buildTaskTree(tasks, grouping = TaskGrouping.TASK_NAME)
        val build = byName.childNodes().single { (it as TaskNameNode).name == "build" }
        val web = build.childNodes().single { (it as TaskNode).projectId == "web" }
        assertEquals("web", render(web)[0].first)
    }

    /** Gives the node below [this] at the path of node texts [names]. */
    private fun DefaultMutableTreeNode.child(vararg names: String): DefaultMutableTreeNode =
        names.fold(this) { node, name -> node.childNodes().single { it.toString() == name } }

    private fun DefaultMutableTreeNode.childNames() = childNodes().map { it.toString() }

    private val none = MoonExplorerBundle.message("tasks.tree.noValue")

    fun testStackAndProjectHasStackNodesWithProjectsAndTasks() {
        val tree = buildTaskTree(tasks, TaskCategory.STACK, TaskGrouping.PROJECT)
        assertEquals(listOf("backend", "frontend", none), tree.childNames())
        assertEquals(listOf("api", "db", "worker"), tree.child("backend").childNames())
        assertTrue(tree.child("backend").childNodes().all { it is ProjectNode })
        assertEquals(listOf("changelog", "lint"), tree.child(none, "scripts").childNames())
        assertTrue(tree.child(none, "scripts").childNodes().all { it is TaskNode && it.isLeaf })
    }

    fun testStackAndTaskNameHasStackNodesWithTaskNames() {
        val tree = buildTaskTree(tasks, TaskCategory.STACK, TaskGrouping.TASK_NAME)
        assertEquals(listOf("backend", "frontend", none), tree.childNames())
        assertEquals(listOf("audit", "build", "format", "lint", "migrate", "start", "test"), tree.child("backend").childNames())
        assertEquals(listOf("api", "db", "worker"), tree.child("backend", "audit").childNames())
        assertTrue(tree.child("backend", "audit").childNodes().all { it is TaskNode && it.showProject && it.isLeaf })
        assertEquals(listOf("changelog", "lint"), tree.child(none).childNames())
    }

    fun testLayerAndProjectHasLayerNodesWithProjectsAndTasks() {
        val tree = buildTaskTree(tasks, TaskCategory.LAYER, TaskGrouping.PROJECT)
        assertEquals(listOf("application", "library", none), tree.childNames())
        assertEquals(listOf("admin", "api", "web", "worker"), tree.child("application").childNames())
        assertEquals(listOf("db", "icons", "ui", "utils"), tree.child("library").childNames())
        assertEquals(listOf("scripts"), tree.child(none).childNames())
        assertEquals(listOf("audit", "build", "format", "lint", "migrate", "test"), tree.child("library", "db").childNames())
    }

    fun testLayerAndTaskNameHasLayerNodesWithTaskNames() {
        val tree = buildTaskTree(tasks, TaskCategory.LAYER, TaskGrouping.TASK_NAME)
        assertEquals(listOf("application", "library", none), tree.childNames())
        assertEquals(listOf("db", "ui", "utils"), tree.child("library", "build").childNames())
        assertEquals(listOf("admin", "api", "web", "worker"), tree.child("application", "build").childNames())
        assertEquals(listOf("scripts"), tree.child(none, "changelog").childNames())
    }

    fun testTagAndProjectHasTagNodesWithProjectsAndTasks() {
        val tree = buildTaskTree(tasks, TaskCategory.TAG, TaskGrouping.PROJECT)
        assertEquals(listOf("internal", "node", "public", "react", "shared", none), tree.childNames())
        assertEquals(listOf("admin", "ui", "web"), tree.child("react").childNames())
        assertEquals(listOf("api", "web"), tree.child("public").childNames())
        assertEquals(listOf("icons", "scripts"), tree.child(none).childNames())
        assertEquals(listOf("changelog", "lint"), tree.child(none, "scripts").childNames())
    }

    fun testProjectWithTwoTagsIsInTheTwoTagNodes() {
        val tree = buildTaskTree(tasks, TaskCategory.TAG)
        val webNodes = tree.childNodes().filter { tag -> tag.childNodes().any { it.toString() == "web" } }
        assertEquals(listOf("public", "react"), webNodes.map { it.toString() })
        assertEquals(tree.child("public", "web").childNames(), tree.child("react", "web").childNames())
    }

    fun testTagAndTaskNameHasTagNodesWithTaskNames() {
        val tree = buildTaskTree(tasks, TaskCategory.TAG, TaskGrouping.TASK_NAME)
        assertEquals(listOf("internal", "node", "public", "react", "shared", none), tree.childNames())
        assertEquals(listOf("admin", "ui", "web"), tree.child("react", "build").childNames())
        assertEquals(listOf("api", "db", "worker"), tree.child("node", "audit").childNames())
        assertTrue(tree.child("react", "build").childNodes().all { it is TaskNode && it.showProject && it.isLeaf })
    }

    fun testNoneNodeIsLastAndGray() {
        val tree = buildTaskTree(tasks, TaskCategory.LAYER)
        val noneNode = tree.childNodes().last() as CategoryNode
        assertNull(noneNode.value)
        assertEquals(TaskCategory.LAYER, noneNode.category)
        assertEquals(none, noneNode.toString())
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, render(noneNode)[0].second)
        val library = tree.childNodes().first { it.toString() == "library" }
        assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, render(library)[0].second)
    }

    fun testOnlyOneCategoryValueStillShowsTheCategoryNode() {
        val single = WorkspaceTasks(listOf(MoonProject("a", listOf(MoonTask("build", "a:build", null)), stack = "frontend")))
        val tree = buildTaskTree(single, TaskCategory.STACK)
        assertEquals(listOf("frontend"), tree.childNames())
        assertEquals(listOf("a"), tree.child("frontend").childNames())
    }

    fun testCategoryNodeKeysAreDifferentForEachCategory() {
        val categories = TaskCategory.entries - TaskCategory.NONE
        val noneKeys = categories.map { (buildTaskTree(tasks, it).childNodes().last() as CategoryNode).key }
        assertEquals(categories.size, noneKeys.toSet().size)
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
