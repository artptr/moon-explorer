package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
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
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
            service.projectDir = { project.basePath?.let(Path::of) }
            service.queryTasks = MoonCli::queryTasks
        } finally {
            super.tearDown()
        }
    }

    private fun awaitState(expected: TasksState) {
        runBlocking { withTimeoutOrNull(10_000) { service.state.first { it == expected } } }
            ?: fail("Expected $expected, but the state is ${service.state.value}")
    }

    fun testRefreshLoadsTheTasks() {
        service.queryTasks = { CliResult.Success(recorded) }
        service.refresh()
        val expected = TasksState.Loaded(QueryTasksParser.parse(recorded))
        awaitState(expected)
        assertEquals(7, expected.tasks.projects.size)
    }

    fun testRefreshShowsTheMoonError() {
        service.queryTasks = { CliResult.Failed("bad config") }
        service.refresh()
        awaitState(TasksState.MoonError("bad config"))
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
        awaitState(TasksState.Loaded(QueryTasksParser.parse(recorded)))
        assertTrue("The first load must be cancelled", cancelled.isCompleted)
        assertEquals(2, calls.get())
    }

    fun testStartupActivityLoadsTheTasks() {
        service.queryTasks = { CliResult.Failed("from startup") }
        runBlocking { MoonTasksStartupActivity().execute(project) }
        awaitState(TasksState.MoonError("from startup"))
    }
}
