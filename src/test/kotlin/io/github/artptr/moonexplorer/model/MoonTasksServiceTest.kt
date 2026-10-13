package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MoonTasksServiceTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private val recorded = TestData.recorded("query-tasks.json")

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.projectDir = { TestData.workspace.toPath() }
    }

    override fun tearDown() {
        try {
            service.projectDir = { project.basePath?.let(Path::of) }
            service.queryTasks = io.github.artptr.moonexplorer.cli.MoonCli::queryTasks
        } finally {
            super.tearDown()
        }
    }

    private fun awaitState(predicate: (TasksState) -> Boolean): TasksState =
        runBlocking { withTimeout(10_000) { service.state.first(predicate) } }

    fun testRefreshLoadsTheTasks() {
        service.queryTasks = { CliResult.Success(recorded) }
        service.refresh()
        val state = awaitState { it is TasksState.Loaded }
        assertEquals(7, (state as TasksState.Loaded).tasks.projects.size)
    }

    fun testRefreshShowsTheMoonError() {
        service.queryTasks = { CliResult.Failed("bad config") }
        service.refresh()
        assertEquals(TasksState.MoonError("bad config"), awaitState { it is TasksState.MoonError })
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
        awaitState { it is TasksState.Loaded }
        assertTrue("The first load must be cancelled", cancelled.isCompleted)
        assertEquals(2, calls.get())
    }

    fun testStartupActivityLoadsTheTasks() {
        service.queryTasks = { CliResult.Failed("from startup") }
        runBlocking { MoonTasksStartupActivity().execute(project) }
        assertEquals(TasksState.MoonError("from startup"), awaitState { it is TasksState.MoonError })
    }
}
