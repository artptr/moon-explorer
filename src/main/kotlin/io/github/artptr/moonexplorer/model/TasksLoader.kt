package io.github.artptr.moonexplorer.model

import com.intellij.openapi.diagnostic.thisLogger
import io.github.artptr.moonexplorer.cli.CliFailure
import io.github.artptr.moonexplorer.cli.CliResult
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Gets the tasks of the workspace and gives the applicable [TasksState]. */
object TasksLoader {

    /**
     * Gives the state after a successful load of [tasks] in the workspace [root]. The function finds the configuration files on the disk.
     * [taskGraph] is the result of `moon task-graph`, or null if this command failed.
     * The state contains [root] as an absolute and normalized path. Thus the users of the state do not normalize it again.
     * Call this function on a background thread.
     */
    internal fun loadedState(tasks: WorkspaceTasks, root: Path, taskGraph: TaskGraph?): TasksState.Loaded {
        val normalizedRoot = root.toAbsolutePath().normalize()
        return TasksState.Loaded(tasks, normalizedRoot, findExistingConfigFiles(tasks, normalizedRoot), taskGraph)
    }

    /**
     * Runs [queryProjects] in the workspace [root] and reads the output. If [root] is null, the function gives [TasksState.NoWorkspace].
     * The output of `moon query projects` contains the tasks, the `stack` value, and the `layer` value of each project.
     * After a success, the function finds the configuration files on the disk.
     * The state keeps [lastGraph], the task graph of the last successful load, until [loadTaskGraph] gives the new task graph.
     * Thus the dependents do not go from the state during a refresh. Call this function on a background thread.
     */
    suspend fun loadTasksIn(root: Path?, queryProjects: suspend (Path) -> CliResult, lastGraph: TaskGraph? = null): TasksState {
        if (root == null) return TasksState.NoWorkspace
        return try {
            when (val result = queryProjects(root)) {
                is CliResult.Success -> loadedState(QueryProjectsParser.parse(result.stdout), root, lastGraph)
                is CliFailure -> TasksState.MoonFailed(moonFailure(result))
            }
        } catch (e: MoonOutputException) {
            TasksState.InvalidOutput(e.problem)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An unexpected error must not keep the tool window in the Loading state.
            thisLogger().warn("Cannot get the moon tasks", e)
            TasksState.UnexpectedError
        }
    }

    /**
     * Runs [taskGraph] in [root] and reads the output. Gives null if the command fails or if the plugin cannot read the output.
     * Call this function on a background thread.
     */
    suspend fun loadTaskGraph(root: Path, taskGraph: suspend (Path) -> CliResult): TaskGraph? = try {
        when (val result = taskGraph(root)) {
            is CliResult.Success -> TaskGraphParser.parse(result.stdout).also { if (it == null) thisLogger().warn("The output of moon task-graph has no task data") }
            is CliFailure -> {
                thisLogger().warn("Cannot get the moon task graph: $result")
                null
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The tree and the run icons do not use the task graph. Thus an error in the graph must not stop the load.
        thisLogger().warn("Cannot get the moon task graph", e)
        null
    }

    private fun moonFailure(result: CliFailure): MoonFailure = when (result) {
        is CliResult.NotFound -> MoonFailure.NotFound(result.examined)
        is CliResult.Failed -> MoonFailure.Error(result.message)
        is CliResult.ExitCode -> MoonFailure.ExitCode(result.code)
        CliResult.Cancelled -> MoonFailure.Cancelled
        is CliResult.StartFailed -> MoonFailure.StartFailed(result.detail)
        CliResult.TimedOut -> MoonFailure.TimedOut
    }
}
