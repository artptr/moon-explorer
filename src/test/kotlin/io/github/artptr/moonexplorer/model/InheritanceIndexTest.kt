package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class InheritanceIndexTest {

    private val state = TestData.recordedState()
    private val index = state.inheritanceIndex

    private fun path(file: String): Path = TestData.workspaceRoot.resolve(file)

    private val all = path(".moon/tasks/all.yml")
    private val frontend = path(".moon/tasks/frontend.yml")

    @Test
    fun projectHasItsInheritedTasks() {
        val inherited = index.project("web")!!.inherited
        assertEquals(
            listOf(
                InheritedTask("lint", "lint", listOf(all)),
                InheritedTask("test", "test", listOf(frontend)),
                InheritedTask("typecheck", "tsc", listOf(frontend)),
            ),
            inherited,
        )
    }

    @Test
    fun includeRemovesTheOtherGlobalTasks() {
        assertEquals(listOf("lint"), index.project("scripts")!!.inherited.map { it.globalId })
    }

    @Test
    fun projectWithoutInheritedTasksHasNoEntry() {
        assertNull(index.project("config"))
        assertFalse("config" in index.file(all)!!.projects)
    }

    @Test
    fun excludedTaskWithLocalTaskOfTheSameIdDoesNotMerge() {
        val web = index.project("web")!!
        assertFalse("format" in web.merged)
        assertFalse("web" in index.file(all)!!.tasks.getValue("format").inheritedBy)
        assertTrue(index.file(all)!!.tasks.getValue("format").mergedIn.none { it.projectId == "web" })
    }

    @Test
    fun localTaskWithTheSameIdMergesWithItsStrategies() {
        val merged = index.project("web")!!.merged.getValue("lint")
        assertEquals(listOf(InheritedTask("lint", "lint", listOf(all))), merged.globalTasks)
        assertEquals(mapOf("args" to "prepend", "deps" to "replace"), merged.strategies)
        assertEquals(
            listOf(MergingProject("web", "lint", mapOf("args" to "prepend", "deps" to "replace"))),
            index.file(all)!!.tasks.getValue("lint").mergedIn,
        )
    }

    @Test
    fun fileHasTheProjectsThatInheritOneOrMoreTasks() {
        val file = index.file(all)!!
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), file.projects)
        assertEquals(setOf("lint", "format"), file.tasks.keys)
        assertFalse("scripts" in file.tasks.getValue("format").inheritedBy)
    }

    @Test
    fun renamedTaskHasItsGlobalIdInTheFileAndItsLocalIdInTheTarget() {
        val typecheck = index.file(frontend)!!.tasks.getValue("typecheck")
        assertEquals("tsc", typecheck.inheritedBy["web"])
        assertEquals(mapOf("web:tsc" to setOf("web:build")), typecheck.dependents)
    }

    @Test
    fun dependentsContainInternalTasks() {
        val lint = index.file(all)!!.tasks.getValue("lint")
        assertEquals(mapOf("ui:lint" to setOf("ui:build", "ui:generate-icons")), lint.dependents)
    }

    @Test
    fun dependentsAreNullWithoutTheTaskGraph() {
        val withoutGraph = TestData.loadedState(TestData.recordedTasks(), taskGraph = null).inheritanceIndex
        assertNull(withoutGraph.file(all)!!.tasks.getValue("lint").dependents)
        assertEquals(1, withoutGraph.file(all)!!.tasks.getValue("lint").mergedIn.size)
    }

    @Test
    fun internalTaskThatMergesHasItsStrategies() {
        val expected = mapOf("args" to "replace")
        assertEquals(expected, index.project("icons")!!.merged.getValue("format").strategies)
        val withoutGraph = TestData.loadedState(TestData.recordedTasks(), taskGraph = null).inheritanceIndex
        assertEquals(expected, withoutGraph.project("icons")!!.merged.getValue("format").strategies)
        assertEquals(listOf("icons", "web"), withoutGraph.file(all)!!.tasks.values.flatMap { it.mergedIn }.map { it.projectId }.sorted())
    }

    @Test
    fun taskGraphValueHasPriorityOverTheQueryValue() {
        val tasks = WorkspaceTasks(
            projects = listOf(MoonProject("web", listOf(MoonTask("build", "web:build", null)))),
            sources = listOf(ProjectSource("web", "apps/web")),
            taskConfigs = mapOf(
                "web" to TaskConfigs(
                    ownTasks = setOf("lint"),
                    mergeStrategies = mapOf("lint" to mapOf("args" to "prepend")),
                    inheritedLayers = mapOf("lint" to listOf(InheritedLayer("lint", ".moon/tasks/all.yml"))),
                ),
            ),
        )
        val graph = TaskGraph(mergeStrategies = mapOf("web:lint" to mapOf("args" to "replace")))
        val merged = TestData.loadedState(tasks, taskGraph = graph).inheritanceIndex.project("web")!!.merged.getValue("lint")
        assertEquals(mapOf("args" to "replace"), merged.strategies)
    }

    @Test
    fun givesTheProjectOfAConfigurationFile() {
        assertEquals("web", index.projectOfFile(path("apps/web/moon.yml")))
        assertNull(index.projectOfFile(all))
    }

    @Test
    fun fileOutsideTheLoadHasNoEntry() {
        assertNull(index.file(path("apps/web/moon.yml")))
    }
}
