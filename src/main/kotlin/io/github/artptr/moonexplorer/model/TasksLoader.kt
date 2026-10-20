package io.github.artptr.moonexplorer.model

import com.intellij.openapi.diagnostic.thisLogger
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Gets the tasks of the workspace and gives the applicable [TasksState]. */
object TasksLoader {

    /**
     * Finds the workspace from [projectDir], runs [queryProjects] in the workspace root, and reads the output.
     * The output of `moon query projects` contains the tasks, the `stack` value, and the `layer` value of each project.
     * Thus the function starts only one moon process.
     * Call this function on a background thread.
     */
    suspend fun loadTasks(projectDir: Path?, queryProjects: suspend (Path) -> CliResult): TasksState {
        val root = projectDir?.let(MoonWorkspaceLocator::findWorkspaceRoot) ?: return TasksState.NoWorkspace
        return try {
            val result = queryProjects(root)
            if (result !is CliResult.Success) return errorState(result)
            TasksState.Loaded(QueryProjectsParser.parse(result.stdout), root)
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
