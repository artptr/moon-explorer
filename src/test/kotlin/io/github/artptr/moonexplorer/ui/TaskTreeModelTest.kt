package io.github.artptr.moonexplorer.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.QueryTasksParser
import io.github.artptr.moonexplorer.testData.TestData
import javax.swing.tree.DefaultMutableTreeNode

class TaskTreeModelTest : BasePlatformTestCase() {

    private val root by lazy { buildTaskTree(QueryTasksParser.parse(TestData.recorded("query-tasks.json"))) }

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
}
