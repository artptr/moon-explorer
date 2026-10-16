package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Removes the state and the load of the previous test. The light project is the same for all tests.
 * Then sets the test workspace and the recorded output of `moon query projects`. Thus the tests do not start moon.
 * The tests that need other output change these values. Call [MoonTasksService.resetForTests] in tearDown.
 */
fun MoonTasksService.prepareForTests() {
    runBlocking { resetForTests() }
    projectDir = { TestData.workspace.toPath() }
    queryProjects = { CliResult.Success(TestData.recorded("query-projects.json")) }
}

/** Waits until the state is equal to [expected]. If this does not occur in 10 seconds, the test fails. */
fun MoonTasksService.awaitState(expected: TasksState) {
    runBlocking { withTimeoutOrNull(10_000) { state.first { it == expected } } }
        ?: throw AssertionError("Expected $expected, but the state is ${state.value}")
}
