package io.github.artptr.moonexplorer.model

import com.intellij.openapi.diagnostic.thisLogger
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Gets the tasks of the workspace and gives the applicable [TasksState]. */
object TasksLoader {

    /**
     * Finds the workspace from [projectDir], runs [queryTasks] and then [queryProjects] in the workspace root, and reads the output.
     * The function runs the two commands one after the other. Thus two moon processes do not use the workspace at the same time.
     * Call this function on a background thread.
     */
    suspend fun loadTasks(
        projectDir: Path?,
        queryTasks: suspend (Path) -> CliResult,
        queryProjects: suspend (Path) -> CliResult,
    ): TasksState {
        val root = projectDir?.let(MoonWorkspaceLocator::findWorkspaceRoot) ?: return TasksState.NoWorkspace
        return try {
            val tasksResult = queryTasks(root)
            if (tasksResult !is CliResult.Success) return errorState(tasksResult)
            val tasks = QueryTasksParser.parse(tasksResult.stdout)
            val projectsResult = queryProjects(root)
            if (projectsResult !is CliResult.Success) return errorState(projectsResult)
            TasksState.Loaded(tasks.withStacks(QueryProjectsParser.parseStacks(projectsResult.stdout)))
        } catch (e: MoonOutputException) {
            TasksState.InvalidOutput(e.message.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An unexpected error must not keep the tool window in the Loading state.
            thisLogger().warn("Cannot get the moon tasks", e)
            TasksState.InvalidOutput(e.message ?: e.javaClass.name)
        }
    }

    private fun errorState(result: CliResult): TasksState = when (result) {
        is CliResult.NotFound -> TasksState.MoonNotFound(result.examined)
        is CliResult.Failed -> TasksState.MoonError(result.message)
        CliResult.TimedOut -> TasksState.MoonTimeout
        is CliResult.Success -> error("A successful result is not an error")
    }
}
