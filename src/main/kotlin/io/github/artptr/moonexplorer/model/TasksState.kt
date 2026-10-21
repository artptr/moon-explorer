package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.cli.CliFailure
import java.nio.file.Path

/** The state of the task data for the tool window. */
sealed interface TasksState {
    /** A load that failed. The tool window or the notification shows a message for the failure. */
    sealed interface Failure : TasksState

    /** The plugin runs `moon query projects`. */
    data object Loading : TasksState

    /** The plugin read the tasks of the workspace. [root] is the workspace root, the directory that contains `.moon/`. */
    data class Loaded(val tasks: WorkspaceTasks, val root: Path) : TasksState

    /**
     * The last load failed after an earlier successful load. [last] is the result of the last successful load.
     * The tree shows [last]. A notification shows the message for [failure]. The toolbar shows the action **Show Refresh Error**.
     */
    data class Stale(val last: Loaded, val failure: Failure) : TasksState

    /** The IDE project does not contain a moon workspace. */
    data object NoWorkspace : TasksState

    /** The `moon` command failed. [cause] tells the cause. */
    data class MoonFailed(val cause: CliFailure) : Failure

    /** The plugin cannot read the output of moon. [problem] tells the cause. */
    data class InvalidOutput(val problem: OutputProblem) : Failure

    /** An unexpected error occurred during the load. The IDE log contains the exception. */
    data object UnexpectedError : Failure
}
