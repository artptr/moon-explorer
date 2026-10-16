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
        runBlocking {
            TasksLoader.loadTasks(workspace.resolve("apps/web")) { dir = it; CliResult.Success("""{"tasks": {}}""") }
        }
        assertEquals(workspace.toAbsolutePath().normalize(), dir)
    }

    @Test
    fun successGivesLoaded() {
        val state = load(workspace, CliResult.Success(TestData.recorded("query-tasks.json")))
        assertTrue(state is TasksState.Loaded)
        assertEquals(7, (state as TasksState.Loaded).tasks.projects.size)
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
        val state = runBlocking {
            TasksLoader.loadTasks(workspace) { throw ExecutionException("cannot start") }
        }
        assertEquals(TasksState.InvalidOutput("cannot start"), state)
    }
}
