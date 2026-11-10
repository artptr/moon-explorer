package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.settings.resetForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Path

/**
 * A test that uses [MoonTasksService]. setUp calls [prepareForTests]. tearDown resets the service and [MoonSettings].
 * The light project is the same for all tests. Thus the next test does not get the state of this test.
 * A subclass that overrides tearDown does its own cleanup first, and then calls `super.tearDown()`.
 */
abstract class MoonTasksServiceTestCase : BasePlatformTestCase() {

    protected lateinit var service: MoonTasksService

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            project.service<MoonSettings>().resetForTests()
            if (::service.isInitialized) runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }
}

/**
 * Removes the state and the load of the previous test. The light project is the same for all tests.
 * Then sets the test workspace and the recorded output of `moon query projects` and `moon task-graph`. Thus the tests do not start moon.
 * The tests that need other output change these values. Call [MoonTasksService.resetForTests] in tearDown, or use [MoonTasksServiceTestCase].
 */
fun MoonTasksService.prepareForTests() {
    runBlocking { resetForTests() }
    projectDir = { TestData.workspace.toPath() }
    queryProjects = { CliResult.Success(TestData.recorded("query-projects.json")) }
    taskGraph = { CliResult.Success(TestData.recorded("task-graph.json")) }
}

/** Waits until the state is equal to [expected]. If this does not occur in 10 seconds, the test fails. */
fun MoonTasksService.awaitState(expected: TasksState) {
    runBlocking { withTimeoutOrNull(10_000) { state.first { it == expected } } }
        ?: throw AssertionError("Expected $expected, but the state is ${state.value}")
}

/** Waits until the deferred value completes. If this does not occur in 10 seconds, the test fails. */
fun CompletableDeferred<Unit>.awaitWithTimeout() {
    runBlocking { withTimeout(10_000) { await() } }
}

/** Loads the recorded tasks and waits for the [TasksState.Loaded] state. */
fun MoonTasksService.loadRecorded() {
    refresh()
    awaitState(TestData.recordedState())
}

/** Loads the `moon query projects` output [json] in [root] and waits for the [TasksState.Loaded] state. Gives the state. */
fun MoonTasksService.loadJson(json: String, root: Path = TestData.workspaceRoot): TasksState.Loaded {
    queryProjects = { CliResult.Success(json) }
    refresh()
    val loaded = TestData.loadedState(QueryProjectsParser.parse(json), root)
    awaitState(loaded)
    return loaded
}

/** Makes a refresh fail with the error text [message], and waits for the [TasksState.MoonFailed] state. */
fun MoonTasksService.loadFailed(message: String) {
    queryProjects = { CliResult.Failed(message) }
    refresh()
    awaitState(TasksState.MoonFailed(MoonFailure.Error(message)))
}

/**
 * Loads the recorded tasks. Then makes a refresh fail with the error text [message] and waits for the [TasksState.Stale] state.
 * Gives the state.
 */
fun MoonTasksService.loadStale(message: String = "refresh failed"): TasksState.Stale {
    loadRecorded()
    queryProjects = { CliResult.Failed(message) }
    refresh()
    val stale = TasksState.Stale(TestData.recordedState(), TasksState.MoonFailed(MoonFailure.Error(message)))
    awaitState(stale)
    return stale
}

/**
 * Starts a refresh that does not complete, and waits until `moon query projects` starts. The query waits until
 * [MoonTasksService.resetForTests] cancels it. If the query does not start in 10 seconds, the test fails. Gives the state during the refresh.
 */
fun MoonTasksService.startEndlessRefresh(): TasksState {
    val started = CompletableDeferred<Unit>()
    queryProjects = { started.complete(Unit); awaitCancellation() }
    refresh()
    started.awaitWithTimeout()
    return state.value
}

/** Loads with no IDE project directory and waits for the [TasksState.NoWorkspace] state. */
fun MoonTasksService.loadNoWorkspace() {
    projectDir = { null }
    refresh()
    awaitState(TasksState.NoWorkspace)
}
