package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/** The state of the task data for the tool window. */
sealed interface TasksState {
    /** The plugin runs `moon query projects`. */
    data object Loading : TasksState

    /** The plugin read the tasks of the workspace. [root] is the workspace root, the directory that contains `.moon/`. */
    data class Loaded(val tasks: WorkspaceTasks, val root: Path) : TasksState

    /** The IDE project does not contain a moon workspace. */
    data object NoWorkspace : TasksState

    /** The plugin cannot find the `moon` executable. [examined] contains the files that the plugin examined before `PATH`. */
    data class MoonNotFound(val examined: List<Path>) : TasksState

    /** The `moon` command gave an error. [text] is the error text from moon. */
    data class MoonError(val text: String) : TasksState

    /** The `moon` command did not complete in the time limit. The plugin stopped the moon process. */
    data object MoonTimeout : TasksState

    /** The plugin cannot read the output of moon. [text] tells the cause. */
    data class InvalidOutput(val text: String) : TasksState
}
