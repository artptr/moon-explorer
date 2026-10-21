package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/** Tells why the `moon` command failed. The `ui` layer uses this type, because it does not use the `cli` layer. */
sealed interface MoonFailure {
    /** The plugin cannot find the `moon` executable. [examined] contains the files that the plugin examined before `PATH`. */
    data class NotFound(val examined: List<Path>) : MoonFailure

    /** The `moon` command gave an error. [text] is the error text from moon. It is not blank. */
    data class Error(val text: String) : MoonFailure

    /** The `moon` command stopped with the exit code [code], which is not 0. moon gave no error text. */
    data class ExitCode(val code: Int) : MoonFailure

    /** The moon process stopped before it completed, but not because of the time limit. moon gave no error text. */
    data object Cancelled : MoonFailure

    /** The plugin cannot start the moon process. [detail] is the error text from the IntelliJ Platform, or null if the platform gave no text. */
    data class StartFailed(val detail: String?) : MoonFailure

    /** The `moon` command did not complete in the time limit. The plugin stopped the moon process. */
    data object TimedOut : MoonFailure
}

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
    data class MoonFailed(val cause: MoonFailure) : Failure

    /** The plugin cannot read the output of moon. [problem] tells the cause. */
    data class InvalidOutput(val problem: OutputProblem) : Failure

    /** An unexpected error occurred during the load. The IDE log contains the exception. */
    data object UnexpectedError : Failure
}
