package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class MoonTasksServiceTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private val recorded = TestData.recorded("query-tasks.json")

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        // The light project is the same for all tests. Remove the state and the load of the previous test.
        runBlocking { service.resetForTests() }
        service.projectDir = { TestData.workspace.toPath() }
        // The tests must not start moon. The tests that need other output change this value.
        service.queryProjects = { CliResult.Success(TestData.recorded("query-projects.json")) }
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    fun testRefreshLoadsTheTasks() {
        service.queryTasks = { CliResult.Success(recorded) }
        service.refresh()
        val stacks = QueryProjectsParser.parseStacks(TestData.recorded("query-projects.json"))
        val expected = TasksState.Loaded(QueryTasksParser.parse(recorded).withStacks(stacks))
        service.awaitState(expected)
        assertEquals(7, expected.tasks.projects.size)
    }

    fun testRefreshShowsTheMoonError() {
        service.queryTasks = { CliResult.Failed("bad config") }
        service.refresh()
        service.awaitState(TasksState.MoonError("bad config"))
    }

    fun testRefreshSetsLoadingBeforeItReturns() {
        service.queryTasks = { CliResult.Failed("first") }
        service.refresh()
        service.awaitState(TasksState.MoonError("first"))
        service.queryTasks = { awaitCancellation() }
        service.refresh()
        assertEquals(TasksState.Loading, service.state.value)
    }

    fun testRefreshUsesTheInputsOfTheCall() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        service.queryTasks = {
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
            service.queryTasks = { CliResult.Failed("second refresh") }
            service.refresh()
            // A change after the call must not go into the load of the call.
            service.queryTasks = { CliResult.Failed("later change") }
            release.complete(Unit)
            service.awaitState(TasksState.MoonError("second refresh"))
        } finally {
            // If the test fails before this step, tearDown must not wait for the first load without end.
            release.complete(Unit)
        }
    }

    fun testRefreshCancelsThePreviousLoad() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        service.queryTasks = {
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
        val stacks = QueryProjectsParser.parseStacks(TestData.recorded("query-projects.json"))
        service.awaitState(TasksState.Loaded(QueryTasksParser.parse(recorded).withStacks(stacks)))
        assertTrue("The first load must be cancelled", cancelled.isCompleted)
        assertEquals(2, calls.get())
    }

    fun testStartupActivityLoadsTheTasks() {
        service.queryTasks = { CliResult.Failed("from startup") }
        runBlocking { MoonTasksStartupActivity().execute(project) }
        service.awaitState(TasksState.MoonError("from startup"))
    }
}
