package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class InheritanceIndexTest {

    private val state = TestData.recordedState()
    private val index = state.inheritanceIndex
    private val withoutGraph = TestData.loadedState(TestData.recordedTasks(), taskGraph = null)

    private fun path(file: String): Path = TestData.workspacePath(file)

    private val all = path(".moon/tasks/all.yml")
    private val frontend = path(".moon/tasks/frontend.yml")
    private val backend = path(".moon/tasks/backend.yml")

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
        assertTrue(web.inherited.none { it.globalId == "format" })
        assertTrue(index.file(all)!!.tasks.getValue("format").mergedIn.none { it.projectId == "web" })
    }

    @Test
    fun localTaskWithTheSameIdMergesWithItsStrategies() {
        val merged = index.project("web")!!.merged.getValue("lint")
        assertEquals(listOf(InheritedTask("lint", "lint", listOf(all))), merged)
        assertEquals(mapOf("args" to "prepend", "deps" to "replace"), state.mergeStrategies("web", "lint"))
        assertEquals(listOf(MergingProject("web", "lint")), index.file(all)!!.tasks.getValue("lint").mergedIn)
    }

    @Test
    fun fileHasTheProjectsThatInheritOneOrMoreTasks() {
        val file = index.file(all)!!
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), file.projects.keys.toList())
        assertEquals(setOf("lint", "format"), file.tasks.keys)
    }

    @Test
    fun renamedTaskHasItsGlobalIdInTheFileAndItsLocalIdInTheTarget() {
        val typecheck = index.file(frontend)!!.tasks.getValue("typecheck")
        assertEquals(mapOf("web:tsc" to setOf("web:build")), state.dependents(typecheck))
    }

    @Test
    fun dependentsContainInternalTasks() {
        val lint = index.file(all)!!.tasks.getValue("lint")
        assertEquals(mapOf("ui:lint" to setOf("ui:build", "ui:generate-icons")), state.dependents(lint))
        assertEquals(2, dependentCount(state.dependents(lint)))
    }

    @Test
    fun dependentsAreEmptyWithoutTheTaskGraph() {
        val lint = withoutGraph.inheritanceIndex.file(all)!!.tasks.getValue("lint")
        assertEquals(emptyMap<String, Set<String>>(), withoutGraph.dependents(lint))
        assertEquals(1, lint.mergedIn.size)
    }

    @Test
    fun internalTaskThatMergesHasItsStrategies() {
        val expected = mapOf("args" to "replace")
        assertTrue("format" in index.project("icons")!!.merged)
        assertEquals(expected, state.mergeStrategies("icons", "format"))
        assertEquals(expected, withoutGraph.mergeStrategies("icons", "format"))
        assertEquals(listOf("icons", "web"), withoutGraph.inheritanceIndex.file(all)!!.tasks.values.flatMap { it.mergedIn }.map { it.projectId }.sorted())
    }

    @Test
    fun internalTaskUsesTheSettingMergeWithoutTheTaskGraph() {
        val expected = mapOf(
            "args" to "prepend", "checks" to "replace", "deps" to "replace", "env" to "replace",
            "inputs" to "replace", "outputs" to "replace", "tags" to "replace", "toolchains" to "replace",
        )
        assertTrue("typecheck" in index.project("icons")!!.merged)
        assertEquals(expected, state.mergeStrategies("icons", "typecheck"))
        assertEquals(expected, withoutGraph.mergeStrategies("icons", "typecheck"))
    }

    @Test
    fun globalTaskHasTheProjectsThatInheritIt() {
        val format = index.file(all)!!.tasks.getValue("format").inheritedBy
        assertFalse("scripts" in format)
        assertFalse("web" in format)
        assertEquals("format", format["api"])
        assertEquals("tsc", index.file(frontend)!!.tasks.getValue("typecheck").inheritedBy["web"])
    }

    @Test
    fun globalTaskRenamedToALocalIdMerges() {
        val merged = index.project("db")!!.merged.getValue("unit")
        assertEquals(listOf(InheritedTask("test", "unit", listOf(backend))), merged)
        assertEquals(listOf(MergingProject("db", "unit")), index.file(backend)!!.tasks.getValue("test").mergedIn)
    }

    @Test
    fun twoGlobalTasksRenamedToOneIdAreTwoInheritedTasks() {
        val check = index.project("worker")!!.inherited.filter { it.localId == "check" }
        assertEquals(listOf("format", "test"), check.map { it.globalId })
        assertEquals("check", index.file(all)!!.tasks.getValue("format").inheritedBy["worker"])
        assertEquals("check", index.file(backend)!!.tasks.getValue("test").inheritedBy["worker"])
    }

    @Test
    fun globalTaskInTwoFilesHasBothFiles() {
        assertEquals(listOf(all, backend), index.project("api")!!.inherited.single { it.globalId == "lint" }.files)
        assertTrue("api" in index.file(all)!!.tasks.getValue("lint").inheritedBy)
        assertTrue("api" in index.file(backend)!!.tasks.getValue("lint").inheritedBy)
        assertFalse("web" in index.file(backend)!!.tasks.getValue("lint").inheritedBy)
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
        assertEquals(mapOf("args" to "replace"), TestData.loadedState(tasks, taskGraph = graph).mergeStrategies("web", "lint"))
        assertEquals(mapOf("args" to "prepend"), TestData.loadedState(tasks, taskGraph = null).mergeStrategies("web", "lint"))
    }

    @Test
    fun givesTheProjectOfAConfigurationFile() {
        assertEquals("web", index.projectOfFile(path("apps/web/moon.yml")))
        assertNull(index.projectOfFile(all))
    }

    @Test
    fun runTargetsAreTheInheritingProjectsWithoutTheInternalTasks() {
        // The task `icons:typecheck` is internal. Thus the hint shows the project `icons`, but the run icon does not run `icons:typecheck`.
        val typecheck = index.file(frontend)!!.tasks.getValue("typecheck")
        assertEquals(listOf("admin", "icons", "ui", "utils", "web"), typecheck.inheritedBy.keys.toList())
        assertEquals(listOf("admin:typecheck", "ui:typecheck", "utils:typecheck", "web:tsc"), typecheck.targets)
        assertEquals(TaskKeyTargets.Shared(typecheck.targets), index.targets(frontend, "typecheck"))
    }

    @Test
    fun newTaskGraphKeepsTheIndex() {
        val updated = state.copy(taskGraph = TaskGraph())
        assertSame(index, updated.inheritanceIndex)
        assertEquals(emptyMap<String, Set<String>>(), updated.dependents(index.file(all)!!.tasks.getValue("lint")))
    }

    @Test
    fun newTasksMakeANewIndex() {
        val updated = state.copy(tasks = state.tasks.copy(projects = state.tasks.projects.filter { it.id != "web" }))
        assertNotSame(index, updated.inheritanceIndex)
        assertFalse("web:test" in updated.inheritanceIndex.file(frontend)!!.tasks.getValue("test").targets)
        assertEquals(state, state.copy())
    }

    @Test
    fun fileOutsideTheLoadHasNoEntry() {
        assertNull(index.file(path("apps/web/moon.yml")))
    }
}
