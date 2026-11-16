package io.github.artptr.moonexplorer.model

import java.nio.file.Path
import java.util.Objects

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

    /** The plugin cannot start the moon process. [detail] is the error text from the IntelliJ Platform, or null if the platform gave no text. [detail] can be blank. */
    data class StartFailed(val detail: String?) : MoonFailure

    /** The `moon` command did not complete in the time limit. The plugin stopped the moon process. */
    data object TimedOut : MoonFailure
}

/** The state of the task data for the tool window. */
sealed interface TasksState {
    /** A load that failed. The tool window or the notification shows a message for the failure. */
    sealed interface Failure : TasksState

    /**
     * The plugin runs `moon query projects`. [last] is the result of the last successful load, or null if no load was successful.
     * During the load, the tree, **Search Everywhere**, and **Run Anything** use [last].
     * [failure] is the failure of the [Stale] state before the load, or null. Thus the toolbar keeps the action **Show Refresh Error** during the load.
     */
    data class Loading(val last: Loaded?, val failure: Failure? = null) : TasksState

    /**
     * The plugin read the tasks of the workspace. [root] is the workspace root, the directory that contains `.moon/`.
     * [root] is absolute and normalized. Use [TasksLoader.loadedState] to make this state.
     * [configFiles] contains the configuration files that existed at the time of the load. Refer to [findExistingConfigFiles].
     * [taskGraph] is the result of `moon task-graph`. During a load, it is the result of the last load until the command completes.
     * The value is null if the command failed, or if no load completed the command.
     * The constructor makes the index. Thus make this state on a background thread.
     *
     * The class is not a data class, because [copy] keeps the index if [tasks] and [configFiles] are the same instances.
     * [equals], [hashCode], and [toString] use the four values, the same as a data class.
     */
    class Loaded private constructor(
        val tasks: WorkspaceTasks,
        val root: Path,
        val configFiles: ExistingConfigFiles,
        val taskGraph: TaskGraph?,
        /**
         * The task inheritance and the targets of the task keys. The index is not a part of [equals].
         * The index does not use [taskGraph]. Thus a state with a new task graph keeps the index of the last state.
         */
        internal val inheritanceIndex: InheritanceIndex,
    ) : TasksState {
        constructor(tasks: WorkspaceTasks, root: Path, configFiles: ExistingConfigFiles, taskGraph: TaskGraph? = null) :
            this(tasks, root, configFiles, taskGraph, InheritanceIndex.of(tasks, configFiles))

        /**
         * Gives a state with the changed values. If [tasks] and [configFiles] are the same instances as in this state, the new state uses the same index.
         * If not, the function makes a new index. Thus call the function on a background thread.
         */
        fun copy(
            tasks: WorkspaceTasks = this.tasks,
            root: Path = this.root,
            configFiles: ExistingConfigFiles = this.configFiles,
            taskGraph: TaskGraph? = this.taskGraph,
        ): Loaded {
            val index = if (tasks === this.tasks && configFiles === this.configFiles) inheritanceIndex else InheritanceIndex.of(tasks, configFiles)
            return Loaded(tasks, root, configFiles, taskGraph, index)
        }

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Loaded && tasks == other.tasks && root == other.root && configFiles == other.configFiles && taskGraph == other.taskGraph

        override fun hashCode(): Int = Objects.hash(tasks, root, configFiles, taskGraph)

        override fun toString(): String = "Loaded(tasks=$tasks, root=$root, configFiles=$configFiles, taskGraph=$taskGraph)"
    }

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

/**
 * Gives the result of the last successful load, or null if the plugin did not load the tasks.
 * For [TasksState.Stale] and [TasksState.Loading], the value is their `last` value, the same as the tree.
 * After [TasksState.NoWorkspace] or a failure without an earlier successful load, the value is null.
 */
val TasksState.lastLoaded: TasksState.Loaded?
    get() = when (this) {
        is TasksState.Loaded -> this
        is TasksState.Stale -> last
        is TasksState.Loading -> last
        else -> null
    }

/**
 * Gives the failure of the last refresh while the tree shows old tasks, or null.
 * The value is not null for [TasksState.Stale], and for [TasksState.Loading] after [TasksState.Stale].
 */
val TasksState.refreshFailure: TasksState.Failure?
    get() = when (this) {
        is TasksState.Stale -> failure
        is TasksState.Loading -> failure
        else -> null
    }

/** Gives the tasks of [lastLoaded], or null if the plugin did not load the tasks. */
val TasksState.loadedTasks: WorkspaceTasks?
    get() = lastLoaded?.tasks
