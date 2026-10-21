package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class MoonTasksServiceTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private val recorded = TestData.recorded("query-projects.json")

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    fun testRefreshLoadsTheTasks() {
        service.queryProjects = { CliResult.Success(recorded) }
        service.refresh()
        val expected = TestData.recordedState()
        service.awaitState(expected)
        assertEquals(9, expected.tasks.projects.size)
    }

    fun testDefaultQueryRunsMoonQueryProjects() {
        // The fake moon is a shell script. Thus this test does not run on Windows.
        if (SystemInfo.isWindows) return
        // The script writes one project. The ID of the project is the first two arguments.
        val moon = FileUtil.createTempDirectory("fake-moon", null, true).resolve("moon").apply {
            writeText("#!/bin/sh\nprintf '{\"projects\": [{\"id\": \"%s-%s\", \"tasks\": {\"t\": {\"id\": \"t\"}}}]}' \"\$1\" \"\$2\"\n")
            setExecutable(true)
        }
        val settings = project.service<MoonSettings>()
        try {
            settings.executablePath = moon.path
            // Use the default query, not the fake from setUp.
            runBlocking { service.resetForTests() }
            service.projectDir = { TestData.workspace.toPath() }
            service.refresh()
            val expected = MoonProject("query-projects", listOf(MoonTask("t", "query-projects:t", null)))
            service.awaitState(TasksState.Loaded(WorkspaceTasks(listOf(expected)), TestData.workspaceRoot))
        } finally {
            settings.executablePath = ""
        }
    }

    fun testTimeoutComesFromTheSettings() {
        val settings = project.service<MoonSettings>()
        try {
            settings.timeoutSeconds = 90
            assertEquals(90_000, service.timeoutMillis())
        } finally {
            settings.timeoutSeconds = MoonSettings.DEFAULT_TIMEOUT_SECONDS
        }
    }

    fun testDefaultQueryStopsMoonAfterTheTimeLimitFromTheSettings() {
        // The fake moon is a shell script. Thus this test does not run on Windows.
        if (SystemInfo.isWindows) return
        // The script does not stop before the time limit. `exec` replaces the shell. Thus the plugin stops the `sleep` process.
        val moon = FileUtil.createTempDirectory("fake-moon", null, true).resolve("moon").apply {
            writeText("#!/bin/sh\nexec sleep 30\n")
            setExecutable(true)
        }
        val settings = project.service<MoonSettings>()
        try {
            settings.executablePath = moon.path
            settings.timeoutSeconds = 1
            // Use the default query, not the fake from setUp.
            runBlocking { service.resetForTests() }
            service.projectDir = { TestData.workspace.toPath() }
            service.refresh()
            service.awaitState(TasksState.MoonFailed(MoonFailure.TimedOut))
        } finally {
            settings.executablePath = ""
            settings.timeoutSeconds = MoonSettings.DEFAULT_TIMEOUT_SECONDS
        }
    }

    fun testRefreshShowsTheMoonError() {
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("bad config")))
    }

    fun testRefreshSetsLoadingBeforeItReturns() {
        service.queryProjects = { CliResult.Failed("first") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("first")))
        service.queryProjects = { awaitCancellation() }
        service.refresh()
        assertEquals(TasksState.Loading, service.state.value)
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
            runBlocking { withTimeout(10_000) { started.await() } }
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
        runBlocking { withTimeout(10_000) { started.await() } }
        service.refresh()
        service.awaitState(TestData.recordedState())
        assertTrue("The first load must be cancelled", cancelled.isCompleted)
        assertEquals(2, calls.get())
    }

    fun testStartupActivityLoadsTheTasks() {
        service.queryProjects = { CliResult.Failed("from startup") }
        runBlocking { MoonTasksStartupActivity().execute(project) }
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("from startup")))
    }

    private val loaded get() = TestData.recordedState()

    fun testFailureAfterASuccessfulLoadGivesStale() {
        service.queryProjects = { CliResult.Success(recorded) }
        service.refresh()
        service.awaitState(loaded)
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("bad config"))))
        service.queryProjects = { CliResult.Success(recorded) }
        service.refresh()
        service.awaitState(loaded)
    }

    fun testNoWorkspaceRemovesTheLastSuccessfulLoad() {
        service.refresh()
        service.awaitState(loaded)
        val empty = FileUtil.createTempDirectory("no-workspace", null, true).toPath()
        service.projectDir = { empty }
        service.refresh()
        service.awaitState(TasksState.NoWorkspace)
        assertNull(service.configWatch.root)
        service.projectDir = { TestData.workspace.toPath() }
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("bad config")))
    }

    fun testLoadSetsTheConfigWatch() {
        service.refresh()
        service.awaitState(loaded)
        assertEquals(TestData.workspaceRoot, service.configWatch.root)
        assertEquals(TestData.recordedTasks().sources, service.configWatch.sources)
        // A failure keeps the sources of the last successful load.
        service.queryProjects = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.Stale(loaded, TasksState.MoonFailed(MoonFailure.Error("bad config"))))
        assertEquals(TestData.recordedTasks().sources, service.configWatch.sources)
    }

    fun testConfigWatchHasTheRootDuringTheFirstLoad() {
        val started = CompletableDeferred<Unit>()
        service.queryProjects = { started.complete(Unit); awaitCancellation() }
        service.refresh()
        runBlocking { withTimeout(10_000) { started.await() } }
        // A change during the first load must cause a refresh. Thus the listener must know the root before moon completes.
        assertEquals(TestData.workspaceRoot, service.configWatch.root)
    }

    fun testTenChangesInOneSecondCauseOneQuery() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        // The default delay is 1 second. The time between the changes is much shorter. Thus a slow computer does not cause a second query.
        repeat(10) {
            service.scheduleRefresh()
            Thread.sleep(100)
        }
        service.awaitState(loaded)
        // Wait longer than the delay. No other refresh must start.
        Thread.sleep(2_000)
        assertEquals(1, calls.get())
    }

    fun testScheduledRefreshWaitsForTheDelay() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        service.refreshDelay = 2_000.milliseconds
        service.scheduleRefresh()
        Thread.sleep(200)
        assertEquals(0, calls.get())
        service.awaitState(loaded)
        assertEquals(1, calls.get())
    }

    fun testManualRefreshCancelsTheScheduledRefresh() {
        val calls = AtomicInteger()
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        service.refreshDelay = 300.milliseconds
        service.scheduleRefresh()
        service.refresh()
        service.awaitState(loaded)
        // Wait much longer than the delay. The cancelled refresh must not start.
        Thread.sleep(1_500)
        assertEquals(1, calls.get())
    }
}
