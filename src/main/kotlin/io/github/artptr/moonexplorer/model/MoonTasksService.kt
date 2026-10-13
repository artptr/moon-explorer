package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.cli.MoonCli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

    /** Runs `moon query tasks` again. If a load runs now, the function cancels it and starts a new load. */
    @Synchronized
    fun refresh() {
        val previous = job
        job = cs.launch {
            previous?.cancelAndJoin()
            _state.value = TasksState.Loading
            _state.value = withContext(Dispatchers.IO) {
                TasksLoader.loadTasks(project.basePath?.let(Path::of), MoonCli::queryTasks)
            }
        }
    }
}
