package io.github.artptr.moonexplorer.model

import com.intellij.execution.ExecutionException
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class TasksLoaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val workspace = TestData.workspace.toPath()

    private val recorded = CliResult.Success(TestData.recorded("query-projects.json"))

    private val recordedGraph = CliResult.Success(TestData.recorded("task-graph.json"))

    private suspend fun loadTasks(dir: Path?, query: suspend (Path) -> CliResult) =
        TasksLoader.loadTasksIn(dir?.let(MoonWorkspaceLocator::findWorkspaceRoot), query)

    private fun loadGraph(graph: suspend (Path) -> CliResult) = runBlocking { TasksLoader.loadTaskGraph(workspace, graph) }

    private fun load(dir: Path?, result: CliResult) = runBlocking { loadTasks(dir) { result } }

    @Test
    fun noProjectDirectoryGivesNoWorkspace() {
        assertEquals(TasksState.NoWorkspace, load(null, CliResult.Success("{}")))
    }

    @Test
    fun directoryWithoutMoonGivesNoWorkspace() {
        assertEquals(TasksState.NoWorkspace, load(temp.newFolder().toPath(), CliResult.Success("{}")))
    }

    @Test
    fun runsTheQueryInTheWorkspaceRoot() {
        var dir: Path? = null
        runBlocking { loadTasks(workspace.resolve("apps/web")) { dir = it; recorded } }
        assertEquals(workspace.toAbsolutePath().normalize(), dir)
    }

    @Test
    fun runsOneQueryForEachLoad() {
        var calls = 0
        runBlocking { loadTasks(workspace) { calls++; recorded } }
        assertEquals(1, calls)
    }

    @Test
    fun successGivesLoaded() {
        assertEquals(TestData.loadedState(TestData.recordedTasks(), taskGraph = null), load(workspace, recorded))
    }

    @Test
    fun stateKeepsTheLastTaskGraph() {
        val graph = TaskGraph(mapOf("a:b" to setOf("c:d")))
        val state = runBlocking { TasksLoader.loadTasksIn(workspace, { recorded }, graph) } as TasksState.Loaded
        assertEquals(graph, state.taskGraph)
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
    fun loadedTasksHaveTheStackOfTheirProject() {
        val state = load(workspace, recorded) as TasksState.Loaded
        val stacks = state.tasks.projects.associate { it.id to it.stack }
        assertEquals("frontend", stacks["web"])
        assertEquals("backend", stacks["api"])
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
    fun unexpectedErrorGivesUnexpectedError() {
        val state = runBlocking { loadTasks(workspace) { throw IllegalStateException("unexpected") } }
        assertEquals(TasksState.UnexpectedError, state)
    }

    @Test
    fun checkedExceptionGivesUnexpectedError() {
        val state = runBlocking { loadTasks(workspace) { throw ExecutionException("cannot start") } }
        assertEquals(TasksState.UnexpectedError, state)
    }
}
