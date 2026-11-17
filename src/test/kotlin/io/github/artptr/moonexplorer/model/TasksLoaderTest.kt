package io.github.artptr.moonexplorer.model

import com.intellij.execution.ExecutionException
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class TasksLoaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val workspace = TestData.workspace.toPath()

    private val recorded = CliResult.Success(TestData.recorded("query-projects.json"))

    private val recordedGraph = CliResult.Success(TestData.recorded("task-graph.json"))

    private suspend fun loadTasks(dir: Path?, last: TasksState.Loaded? = null, query: suspend (Path) -> CliResult) =
        TasksLoader.loadTasksIn(dir?.let(MoonWorkspaceLocator::findWorkspaceRoot), query, last)

    private fun loadGraph(graph: suspend (Path) -> CliResult) = runBlocking { TasksLoader.loadTaskGraph(workspace, graph) }

    private fun load(dir: Path?, result: CliResult, last: TasksState.Loaded? = null) = runBlocking { loadTasks(dir, last) { result } }

    @Test
    fun noProjectDirectoryGivesNoWorkspace() {
        assertEquals(TasksState.NoWorkspace, load(null, CliResult.Success("{}")))
    }

    @Test
    fun successGivesLoaded() {
        assertEquals(TestData.loadedState(TestData.recordedTasks(), taskGraph = null), load(workspace, recorded))
    }

    @Test
    fun newResultKeepsTheLastTaskGraph() {
        val graph = TaskGraph(mapOf("a:b" to setOf("c:d")))
        val last = TestData.loadedState(WorkspaceTasks(emptyList()), taskGraph = graph)
        val state = load(workspace, recorded, last) as TasksState.Loaded
        assertNotSame(last, state)
        assertEquals(TestData.recordedTasks(), state.tasks)
        assertEquals(graph, state.taskGraph)
    }

    @Test
    fun sameResultGivesTheLastInstance() {
        val last = TestData.loadedState(TestData.recordedTasks())
        assertSame(last, load(workspace, recorded, last))
    }

    @Test
    fun differentRootGivesANewState() {
        // The tasks are the same, but the root is different. Thus the configuration files are different too.
        val last = TestData.loadedState(TestData.recordedTasks(), temp.root.toPath())
        val state = load(workspace, recorded, last) as TasksState.Loaded
        assertNotSame(last, state)
        assertEquals(TestData.workspaceRoot, state.root)
    }

    @Test
    fun newConfigFileGivesANewStateAndANewIndex() {
        val root = temp.root.toPath()
        val projectDir = Files.createDirectories(root.resolve("p"))
        val tasks = WorkspaceTasks(
            projects = listOf(MoonProject("p", listOf(MoonTask("build", "p:build", null)))),
            sources = listOf(ProjectSource("p", "p")),
            taskConfigs = mapOf("p" to TaskConfigs(ownTasks = setOf("build"))),
        )
        val first = TasksLoader.loadedState(tasks, root)
        assertSame(first, TasksLoader.loadedState(tasks, root, first))
        val file = Files.writeString(projectDir.resolve("moon.yml"), "").toAbsolutePath().normalize()
        val second = TasksLoader.loadedState(tasks, root, first)
        assertNotSame(first, second)
        assertNotSame(first.inheritanceIndex, second.inheritanceIndex)
        assertNull(first.inheritanceIndex.targets(file, "build"))
        assertEquals(TaskKeyTargets.Single("p:build"), second.inheritanceIndex.targets(file, "build"))
        // The file goes away again. Thus the next load gives a new state without the file.
        Files.delete(file)
        val third = TasksLoader.loadedState(tasks, root, second)
        assertNotSame(second, third)
        assertEquals(emptyMap<String, Path>(), third.configFiles.projects)
    }

    @Test
    fun loadTaskGraphReadsTheOutput() {
        assertEquals(TestData.recordedTaskGraph(), loadGraph { recordedGraph })
    }

    @Test
    fun loadTaskGraphGivesNullForAFailure() {
        val results = listOf<suspend (Path) -> CliResult>(
            { CliResult.Failed("moon cannot make the graph") },
            { CliResult.TimedOut },
            { CliResult.Success("not JSON") },
            { CliResult.Success("{}") },
            { throw IllegalStateException("unexpected") },
        )
        results.forEach { assertNull(loadGraph(it)) }
    }

    @Test(expected = CancellationException::class)
    fun cancellationOfTheTaskGraphStopsTheLoad() {
        loadGraph { throw CancellationException("cancelled") }
    }

    @Test
    fun cliFailureGivesMoonFailedWithTheSameCause() {
        val examined = listOf(workspace.resolve("node_modules/.bin/moon"))
        val failures = mapOf(
            CliResult.NotFound(examined) to MoonFailure.NotFound(examined),
            CliResult.Failed("bad config") to MoonFailure.Error("bad config"),
            CliResult.ExitCode(3) to MoonFailure.ExitCode(3),
            CliResult.Cancelled to MoonFailure.Cancelled,
            CliResult.StartFailed("cannot start") to MoonFailure.StartFailed("cannot start"),
            CliResult.TimedOut to MoonFailure.TimedOut,
        )
        failures.forEach { (result, cause) -> assertEquals(TasksState.MoonFailed(cause), load(workspace, result)) }
    }

    @Test
    fun outputThatIsNotJsonGivesInvalidOutput() {
        val state = load(workspace, CliResult.Success("not JSON"))
        assertTrue("State: $state", (state as? TasksState.InvalidOutput)?.problem is OutputProblem.NotJson)
    }

    @Test
    fun outputWithoutProjectsGivesInvalidOutput() {
        assertEquals(TasksState.InvalidOutput(OutputProblem.NoProjects), load(workspace, CliResult.Success("{}")))
    }

    @Test
    fun unexpectedErrorOrCheckedExceptionGivesUnexpectedError() {
        for (exception in listOf(IllegalStateException("unexpected"), ExecutionException("cannot start"))) {
            val state = runBlocking { loadTasks(workspace) { throw exception } }
            assertEquals("Exception $exception", TasksState.UnexpectedError, state)
        }
    }
}
