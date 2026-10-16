package io.github.artptr.moonexplorer.model

import com.intellij.execution.ExecutionException
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private fun load(dir: Path?, result: CliResult) = runBlocking { TasksLoader.loadTasks(dir) { result } }

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
        runBlocking { TasksLoader.loadTasks(workspace.resolve("apps/web")) { dir = it; recorded } }
        assertEquals(workspace.toAbsolutePath().normalize(), dir)
    }

    @Test
    fun runsOneQueryForEachLoad() {
        var calls = 0
        runBlocking { TasksLoader.loadTasks(workspace) { calls++; recorded } }
        assertEquals(1, calls)
    }

    @Test
    fun successGivesLoaded() {
        assertEquals(TasksState.Loaded(TestData.recordedTasks()), load(workspace, recorded))
    }

    @Test
    fun loadedTasksHaveTheStackOfTheirProject() {
        val state = load(workspace, recorded) as TasksState.Loaded
        val stacks = state.tasks.projects.associate { it.id to it.stack }
        assertEquals("frontend", stacks["web"])
        assertEquals("backend", stacks["api"])
    }

    @Test
    fun notFoundGivesMoonNotFound() {
        val examined = listOf(workspace.resolve("node_modules/.bin/moon"))
        assertEquals(TasksState.MoonNotFound(examined), load(workspace, CliResult.NotFound(examined)))
    }

    @Test
    fun failureGivesMoonError() {
        assertEquals(TasksState.MoonError("bad config"), load(workspace, CliResult.Failed("bad config")))
    }

    @Test
    fun timeoutGivesMoonTimeout() {
        assertEquals(TasksState.MoonTimeout, load(workspace, CliResult.TimedOut))
    }

    @Test
    fun outputThatIsNotJsonGivesInvalidOutput() {
        assertTrue(load(workspace, CliResult.Success("not JSON")) is TasksState.InvalidOutput)
    }

    @Test
    fun unexpectedErrorGivesInvalidOutput() {
        val state = runBlocking { TasksLoader.loadTasks(workspace) { throw IllegalStateException("unexpected") } }
        assertEquals(TasksState.InvalidOutput("unexpected"), state)
    }

    @Test
    fun checkedExceptionGivesInvalidOutput() {
        val state = runBlocking { TasksLoader.loadTasks(workspace) { throw ExecutionException("cannot start") } }
        assertEquals(TasksState.InvalidOutput("cannot start"), state)
    }
}
