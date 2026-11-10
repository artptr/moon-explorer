package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.testData.TestData
import io.github.artptr.moonexplorer.testData.fakeMoonExecutable
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class MoonTasksServiceTest : MoonTasksServiceTestCase() {

    private val recorded = TestData.recorded("query-projects.json")

    private val loaded get() = TestData.recordedState()

    fun testDefaultQueryRunsMoonQueryProjects() {
        // The fake moon is a shell script. Thus this test does not run on Windows.
        if (SystemInfo.isWindows) return
        // The script writes one project. The ID of the project is the first two arguments.
        // For the task graph, the script writes one task. The target of the task is the first two arguments.
        val moon = fakeMoonExecutable(
            content = "#!/bin/sh\n" +
                "if [ \"\$1\" = task-graph ]; then\n" +
                "  printf '{\"data\": {\"0\": {\"target\": \"%s-%s\", \"deps\": [{\"target\": \"a:b\"}]}}}' \"\$1\" \"\$2\"\n" +
                "else\n" +
                "  printf '{\"projects\": [{\"id\": \"%s-%s\", \"tasks\": {\"t\": {\"id\": \"t\"}}}]}' \"\$1\" \"\$2\"\n" +
                "fi\n",
        )
        project.service<MoonSettings>().executablePath = moon.toString()
        // Use the default query, not the fake from setUp.
        runBlocking { service.resetForTests() }
        service.projectDir = { TestData.workspace.toPath() }
        service.refresh()
        val expected = MoonProject("query-projects", listOf(MoonTask("t", "query-projects:t", null)))
        val graph = TaskGraph(mapOf("a:b" to setOf("task-graph---json")))
        service.awaitState(TestData.loadedState(WorkspaceTasks(listOf(expected)), taskGraph = graph))
    }

    fun testServiceShowsTheTasksBeforeTheTaskGraphCompletes() {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        service.taskGraph = {
            started.complete(Unit)
            finish.await()
            CliResult.Success(TestData.recorded("task-graph.json"))
        }
        service.refresh()
        service.awaitState(TestData.loadedState(TestData.recordedTasks(), taskGraph = null))
        started.awaitWithTimeout()
        finish.complete(Unit)
        service.awaitState(loaded)
    }

    fun testRefreshKeepsTheLastTaskGraphUntilTheCommandCompletes() {
        service.loadRecorded()
        val last = service.state.value
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        service.taskGraph = {
            started.complete(Unit)
            finish.await()
            CliResult.Success("""{"data": {}}""")
        }
        service.refresh()
        started.awaitWithTimeout()
        // The result of moon query projects did not change. Thus the state is the same instance, with the last task graph.
        assertSame(last, service.state.value)
        finish.complete(Unit)
        service.awaitState(TestData.loadedState(TestData.recordedTasks(), taskGraph = TaskGraph()))
    }

    fun testTaskGraphFailureRemovesOnlyTheTaskGraph() {
        service.loadRecorded()
        service.taskGraph = { CliResult.Failed("moon cannot make the graph") }
        service.refresh()
        service.awaitState(TestData.loadedState(TestData.recordedTasks(), taskGraph = null))
    }

    fun testNewLoadCancelsTheTaskGraphOfTheLastLoad() {
        val started = CompletableDeferred<Unit>()
        service.taskGraph = { started.complete(Unit); awaitCancellation() }
        service.refresh()
        started.awaitWithTimeout()
        service.taskGraph = { CliResult.Success(TestData.recorded("task-graph.json")) }
        service.refresh()
        service.awaitState(loaded)
    }

    fun testTimeoutComesFromTheSettings() {
        project.service<MoonSettings>().timeoutSeconds = 90
        assertEquals(90_000, service.timeoutMillis())
    }

    fun testDefaultQueryStopsMoonAfterTheTimeLimitFromTheSettings() {
        // The fake moon is a shell script. Thus this test does not run on Windows.
        if (SystemInfo.isWindows) return
        // The script does not stop before the time limit. `exec` replaces the shell. Thus the plugin stops the `sleep` process.
        val moon = fakeMoonExecutable(content = "#!/bin/sh\nexec sleep 30\n")
        val settings = project.service<MoonSettings>()
        settings.executablePath = moon.toString()
        settings.timeoutSeconds = 1
        // Use the default query, not the fake from setUp.
        runBlocking { service.resetForTests() }
        service.projectDir = { TestData.workspace.toPath() }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.TimedOut))
    }

    fun testRefreshAfterALoadKeepsTheLastLoad() {
        service.loadRecorded()
        val state = service.startEndlessRefresh()
        assertEquals(TasksState.Loading(loaded), state)
        assertEquals(TestData.recordedTasks(), state.loadedTasks)
    }

    fun testRefreshAfterAFailedRefreshKeepsTheLastLoad() {
        val stale = service.loadStale()
        assertEquals(TasksState.Loading(stale.last, stale.failure), service.startEndlessRefresh())
    }

    fun testSecondRefreshDuringALoadKeepsTheFailure() {
        val stale = service.loadStale()
        service.startEndlessRefresh()
        assertEquals(TasksState.Loading(stale.last, stale.failure), service.startEndlessRefresh())
    }

    fun testRefreshAfterAFailureWithoutAnEarlierLoadHasNoFailure() {
        service.loadFailed("bad config")
        // refresh sets the Loading state before it returns.
        assertEquals(TasksState.Loading(null), service.startEndlessRefresh())
    }

    fun testRefreshAfterNoWorkspaceDoesNotKeepTheOldTasks() {
        service.loadRecorded()
        service.loadNoWorkspace()
        service.projectDir = { TestData.workspace.toPath() }
        assertEquals(TasksState.Loading(null), service.startEndlessRefresh())
    }

    fun testRefreshUsesTheInputsOfTheCall() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        service.queryProjects = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                // Keep the first load active after the cancel. Thus the second load waits for it.
                withContext(NonCancellable) { release.await() }
            }
        }
        try {
            service.refresh()
            started.awaitWithTimeout()
            service.queryProjects = { CliResult.Failed("second refresh") }
            service.refresh()
            // A change after the call must not go into the load of the call.
            service.queryProjects = { CliResult.Failed("later change") }
            release.complete(Unit)
            service.awaitState(TasksState.MoonFailed(MoonFailure.Error("second refresh")))
        } finally {
            // If the test fails before this step, tearDown must not wait for the first load without end.
            release.complete(Unit)
        }
    }

    fun testRefreshCancelsThePreviousLoad() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        service.queryProjects = {
            if (calls.incrementAndGet() == 1) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            } else {
                CliResult.Success(recorded)
            }
        }
        service.refresh()
        started.awaitWithTimeout()
        service.refresh()
        service.awaitState(loaded)
        assertTrue("The first load must be cancelled", cancelled.isCompleted)
        assertEquals(2, calls.get())
    }

    fun testStartupActivityLoadsTheTasks() {
        service.queryProjects = { CliResult.Failed("from startup") }
        runBlocking { MoonTasksStartupActivity().execute(project) }
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("from startup")))
    }

    fun testFailureAfterASuccessfulLoadGivesStaleAndASuccessGivesLoaded() {
        service.loadStale("bad config")
        service.queryProjects = { CliResult.Success(recorded) }
        service.refresh()
        service.awaitState(loaded)
    }

    fun testRefreshWithTheSameResultKeepsTheLoadedInstance() {
        service.loadRecorded()
        val first = service.state.value
        // refresh sets the Loading state before it returns. Thus awaitState waits for the result of the new load.
        service.refresh()
        service.awaitState(loaded)
        assertSame(first, service.state.value)
    }

    fun testNoWorkspaceRemovesTheLastSuccessfulLoad() {
        service.loadRecorded()
        val empty = FileUtil.createTempDirectory("no-workspace", null, true).toPath()
        service.projectDir = { empty }
        service.refresh()
        service.awaitState(TasksState.NoWorkspace)
        assertNull(service.configWatch.root)
        assertEmpty(service.configWatch.sources)
        // A failure after the next refresh is not stale.
        service.projectDir = { TestData.workspace.toPath() }
        service.loadFailed("bad config")
    }

    fun testLoadSetsTheConfigWatch() {
        service.loadRecorded()
        assertEquals(TestData.workspaceRoot, service.configWatch.root)
        assertEquals(TestData.recordedTasks().sources, service.configWatch.sources)
        // A failure keeps the sources of the last successful load.
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("bad config"))))
        assertEquals(TestData.recordedTasks().sources, service.configWatch.sources)
    }

    fun testConfigWatchKeepsTheSourcesDuringARefresh() {
        service.loadRecorded()
        service.startEndlessRefresh()
        assertEquals(TestData.workspaceRoot, service.configWatch.root)
        assertEquals(TestData.recordedTasks().sources, service.configWatch.sources)
    }

    fun testConfigWatchHasTheRootDuringTheFirstLoad() {
        service.startEndlessRefresh()
        // A change during the first load must cause a refresh. Thus the listener must know the root before moon completes.
        assertEquals(TestData.workspaceRoot, service.configWatch.root)
    }

    fun testManyScheduledRefreshesCauseOneQuery() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        // The time between the calls is much shorter than the delay. Thus a slow computer does not cause a second query.
        service.refreshDelay = 300.milliseconds
        repeat(10) { service.scheduleRefresh() }
        service.awaitState(loaded)
        // Wait longer than the delay. No other refresh must start.
        Thread.sleep(600)
        assertEquals(1, calls.get())
    }

    fun testScheduledRefreshWaitsForTheDelay() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        service.refreshDelay = 500.milliseconds
        service.scheduleRefresh()
        Thread.sleep(100)
        assertEquals(0, calls.get())
        service.awaitState(loaded)
        assertEquals(1, calls.get())
    }

    fun testManualRefreshCancelsTheScheduledRefresh() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        service.refreshDelay = 200.milliseconds
        service.scheduleRefresh()
        service.refresh()
        service.awaitState(loaded)
        // Wait longer than the delay. The cancelled refresh must not start.
        Thread.sleep(600)
        assertEquals(1, calls.get())
    }
}
