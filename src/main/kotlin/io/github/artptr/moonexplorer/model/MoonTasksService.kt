package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** Keeps the tasks of the moon workspace for the IDE project. */
@Service(Service.Level.PROJECT)
class MoonTasksService(private val project: Project, private val cs: CoroutineScope) {

    private val _state = MutableStateFlow<TasksState>(TasksState.Loading)

    /** The current state. The tool window shows this state. */
    val state: StateFlow<TasksState> = _state.asStateFlow()

    /** The coroutine scope of the service. The tool window uses it to collect [state]. */
    val scope: CoroutineScope get() = cs

    private var job: Job? = null

    /** Gives the IDE project directory. Tests change this value. */
    internal var projectDir: () -> Path? = { project.basePath?.let(Path::of) }

    /** Runs `moon query tasks`. Tests change this value to a fake. */
    internal var queryTasks: suspend (Path) -> CliResult = MoonCli::queryTasks

    /** Runs `moon query tasks` again. If a load runs now, the function cancels it and starts a new load. */
    @Synchronized
    fun refresh() {
        val previous = job
        // Cancel the previous load now. Thus two moon processes do not run at the same time for long.
        previous?.cancel()
        job = cs.launch {
            previous?.join()
            _state.value = TasksState.Loading
            _state.value = withContext(Dispatchers.IO) { TasksLoader.loadTasks(projectDir(), queryTasks) }
        }
    }
}
