package io.github.artptr.moonexplorer.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Waits until the state is equal to [expected]. If this does not occur in 10 seconds, the test fails. */
fun MoonTasksService.awaitState(expected: TasksState) {
    runBlocking { withTimeoutOrNull(10_000) { state.first { it == expected } } }
        ?: throw AssertionError("Expected $expected, but the state is ${state.value}")
}
