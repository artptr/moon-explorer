package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import java.nio.file.Path

/** Gets the tasks of the workspace and gives the applicable [TasksState]. */
object TasksLoader {

    /**
     * Finds the workspace from [projectDir], runs [queryTasks] in the workspace root, and reads the output.
     * Call this function on a background thread.
     */
    suspend fun loadTasks(projectDir: Path?, queryTasks: suspend (Path) -> CliResult): TasksState {
        val root = projectDir?.let(MoonWorkspaceLocator::findWorkspaceRoot) ?: return TasksState.NoWorkspace
        return when (val result = queryTasks(root)) {
            CliResult.NotFound -> TasksState.MoonNotFound
            is CliResult.Failed -> TasksState.MoonError(result.message)
            is CliResult.Success -> try {
                TasksState.Loaded(QueryTasksParser.parse(result.stdout))
            } catch (e: MoonOutputException) {
                TasksState.InvalidOutput(e.message.orEmpty())
            }
        }
    }
}
