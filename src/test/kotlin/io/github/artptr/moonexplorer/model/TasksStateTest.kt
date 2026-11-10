package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

/** The consumers of [lastLoaded], [loadedTasks], and [refreshFailure] only check one state each. This test examines all states. */
class TasksStateTest {

    private val loaded = TestData.recordedState()
    private val failure = TasksState.MoonFailed(MoonFailure.Error("bad config"))

    /** Each row is a state, its expected [lastLoaded] value, and its expected [refreshFailure] value. */
    private val rows: List<Triple<TasksState, TasksState.Loaded?, TasksState.Failure?>> = listOf(
        Triple(TasksState.Loading(null), null, null),
        Triple(loaded, loaded, null),
        Triple(TasksState.Loading(loaded), loaded, null),
        Triple(TasksState.Stale(loaded, failure), loaded, failure),
        Triple(TasksState.Loading(loaded, failure), loaded, failure),
        Triple(TasksState.NoWorkspace, null, null),
        Triple(failure, null, null),
        Triple(TasksState.InvalidOutput(OutputProblem.NotJson(null)), null, null),
        Triple(TasksState.UnexpectedError, null, null),
    )

    @Test
    fun lastLoadedAndRefreshFailureOfEachState() {
        for ((state, lastLoaded, refreshFailure) in rows) {
            assertEquals("lastLoaded of $state", lastLoaded, state.lastLoaded)
            assertEquals("loadedTasks of $state", lastLoaded?.tasks, state.loadedTasks)
            assertEquals("refreshFailure of $state", refreshFailure, state.refreshFailure)
        }
    }
}
