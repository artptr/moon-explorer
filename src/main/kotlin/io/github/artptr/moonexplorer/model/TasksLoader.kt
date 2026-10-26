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
     * The state contains [root] as an absolute and normalized path. Thus the users of the state do not normalize it again.
     * Call this function on a background thread.
     */
    internal fun loadedState(tasks: WorkspaceTasks, root: Path): TasksState.Loaded {
        val normalizedRoot = root.toAbsolutePath().normalize()
        return TasksState.Loaded(tasks, normalizedRoot, findExistingConfigFiles(tasks, normalizedRoot))
    }

    /**
     * Runs [queryProjects] in the workspace [root] and reads the output. If [root] is null, the function gives [TasksState.NoWorkspace].
     * The output of `moon query projects` contains the tasks, the `stack` value, and the `layer` value of each project.
     * Thus the function starts only one moon process. After a success, the function finds the configuration files on the disk.
     * Call this function on a background thread.
     */
    suspend fun loadTasksIn(root: Path?, queryProjects: suspend (Path) -> CliResult): TasksState {
        if (root == null) return TasksState.NoWorkspace
        return try {
            when (val result = queryProjects(root)) {
                is CliResult.Success -> loadedState(QueryProjectsParser.parse(result.stdout), root)
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

    private fun moonFailure(result: CliFailure): MoonFailure = when (result) {
        is CliResult.NotFound -> MoonFailure.NotFound(result.examined)
        is CliResult.Failed -> MoonFailure.Error(result.message)
        is CliResult.ExitCode -> MoonFailure.ExitCode(result.code)
        CliResult.Cancelled -> MoonFailure.Cancelled
        is CliResult.StartFailed -> MoonFailure.StartFailed(result.detail)
        CliResult.TimedOut -> MoonFailure.TimedOut
    }
}
