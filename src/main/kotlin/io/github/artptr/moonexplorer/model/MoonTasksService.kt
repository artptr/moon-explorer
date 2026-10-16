package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import io.github.artptr.moonexplorer.settings.MoonSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Keeps the tasks of the moon workspace for the IDE project.
 * [scope] is the coroutine scope of the service. The tool window uses it to collect [state].
 */
@Service(Service.Level.PROJECT)
class MoonTasksService(private val project: Project, val scope: CoroutineScope) {

    private val _state = MutableStateFlow<TasksState>(TasksState.Loading)

    /** The current state. The tool window shows this state. */
    val state: StateFlow<TasksState> = _state.asStateFlow()

    private var job: Job? = null

    private val defaultProjectDir: () -> Path? = { project.basePath?.let(Path::of) }
    private val defaultQueryProjects: suspend (Path) -> CliResult = { root -> query(root, "projects") }

    /**
     * Gives the IDE project directory. Tests change this value.
     * Change it only on the test thread, before you call [refresh]. Each load uses the value from its call.
     */
    internal var projectDir: () -> Path? = defaultProjectDir

    /**
     * Runs `moon query projects`. Tests change this value to a fake.
     * Change it only on the test thread, before you call [refresh]. Each load uses the value from its call.
     */
    internal var queryProjects: suspend (Path) -> CliResult = defaultQueryProjects

    /** Runs `moon query projects` again. If a load runs now, the function cancels it and starts a new load. */
    @Synchronized
    fun refresh() {
        val previous = job
        // Cancel the previous load now. Thus two moon processes do not run at the same time for long.
        previous?.cancel()
        // Show the Loading state before the function returns, not when the new coroutine starts.
        _state.value = TasksState.Loading
        // Read the inputs now. Thus a later change of these values does not go into this load.
        val projectDirFn = projectDir
        val queryProjectsFn = queryProjects
        job = scope.launch {
            previous?.join()
            _state.value = withContext(Dispatchers.IO) { TasksLoader.loadTasks(projectDirFn(), queryProjectsFn) }
        }
    }

    /**
     * Runs `moon query <subject>` in [root] with the executable of [MoonExecutableService].
     * Each call gets the current time limit from [timeoutMillis].
     */
    private suspend fun query(root: Path, subject: String): CliResult {
        val search = project.service<MoonExecutableService>().findInWorkspace(root)
        val executable = search.executable ?: return CliResult.NotFound(search.examined)
        return MoonCli.query(executable, root, subject, timeoutMillis())
    }

    /** Gives the time limit from [MoonSettings] in milliseconds. */
    internal fun timeoutMillis(): Int = project.service<MoonSettings>().timeoutSeconds * 1000

    /**
     * Cancels the load, waits until it stops, and sets the state to [TasksState.Loading].
     * Also sets [projectDir] and [queryProjects] to their default values. Only tests use this function.
     * Call it only on the test thread.
     */
    @TestOnly
    internal suspend fun resetForTests() {
        val previous = synchronized(this) {
            projectDir = defaultProjectDir
            queryProjects = defaultQueryProjects
            job.also { job = null }
        }
        previous?.cancelAndJoin()
        _state.value = TasksState.Loading
    }
}
