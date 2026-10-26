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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the tasks of the moon workspace for the IDE project.
 * [scope] is the coroutine scope of the service. The tool window uses it to collect [state].
 */
@Service(Service.Level.PROJECT)
class MoonTasksService(private val project: Project, val scope: CoroutineScope) {

    private val _state = MutableStateFlow<TasksState>(TasksState.Loading(null))

    /** The current state. The tool window shows this state. */
    val state: StateFlow<TasksState> = _state.asStateFlow()

    private var job: Job? = null

    /** The refresh that [scheduleRefresh] started. It waits for [refreshDelay]. The value is null if no refresh waits. */
    private var pending: Job? = null

    /** The data of the last load for [MoonConfigFileListener]. */
    @Volatile
    internal var configWatch: ConfigWatch = ConfigWatch(null, null)
        private set

    private val defaultProjectDir: () -> Path? = { project.basePath?.let(Path::of) }
    private val defaultQueryProjects: suspend (Path) -> CliResult = ::queryProjects

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

    /**
     * The time that [scheduleRefresh] waits before the refresh. Tests change this value.
     * Change it only on the test thread, before you call [scheduleRefresh].
     */
    internal var refreshDelay: Duration = REFRESH_DELAY

    /**
     * Refreshes the tasks after [refreshDelay]. If the function gets a new call in this time, it starts to wait again.
     * Thus many changes in a short time cause only one run of `moon query projects`.
     */
    @Synchronized
    fun scheduleRefresh() {
        pending?.cancel()
        val wait = refreshDelay
        pending = scope.launch {
            delay(wait)
            synchronized(this@MoonTasksService) {
                // A new call can cancel this job after the delay. Then the new job refreshes.
                if (isActive) startLoad()
            }
        }
    }

    /**
     * Runs `moon query projects` again. If a load runs now, the function cancels it and starts a new load.
     * The function also cancels a refresh that [scheduleRefresh] started. Thus a manual refresh does not cause a second refresh.
     */
    @Synchronized
    fun refresh() {
        pending?.cancel()
        pending = null
        startLoad()
    }

    /** Starts a new load. If a load runs now, the function cancels it. Call this function only with the lock of the service. */
    private fun startLoad() {
        val previous = job
        // Cancel the previous load now. Thus two moon processes do not run at the same time for long.
        previous?.cancel()
        // Show the Loading state before the function returns, not when the new coroutine starts.
        // The state keeps the last successful load. Thus the tree, **Search Everywhere**, and **Run Anything** keep the tasks during the load.
        // The state also keeps the failure of the last refresh. Thus the user can see the error during the load.
        val current = _state.value
        _state.value = TasksState.Loading(current.lastLoaded, current.refreshFailure)
        // Read the inputs now. Thus a later change of these values does not go into this load.
        val projectDirFn = projectDir
        val queryProjectsFn = queryProjects
        job = scope.launch {
            previous?.join()
            _state.value = withContext(Dispatchers.IO) {
                val dir = projectDirFn()
                val root = project.service<MoonExecutableService>().workspaceRoot(dir)
                // The code gets the last successful load after the old load stops. The old load can set its result before it stops.
                val last = _state.value.lastLoaded
                // The service gives this root to the listener before moon starts. Thus a change during the load also causes a refresh.
                watchConfig(root, dir, last)
                val result = TasksLoader.loadTasksIn(root, queryProjectsFn)
                // A failure after a successful load gives Stale. Thus the tree keeps the last correct tasks.
                // NoWorkspace does not keep the last load, because the old tasks are not correct.
                val state = if (result is TasksState.Failure && last != null) TasksState.Stale(last, result) else result
                // A failed load keeps the last successful load. Then the watch does not change.
                if (state.lastLoaded !== last) watchConfig(root, dir, state.lastLoaded)
                state
            }
        }
    }

    /** Sets [configWatch] for [root] and [projectDir]. The sources come from [last], the result of the last successful load. */
    private fun watchConfig(root: Path?, projectDir: Path?, last: TasksState.Loaded?) {
        configWatch = ConfigWatch(root, projectDir, last?.tasks?.sources.orEmpty())
    }

    /**
     * Runs `moon query projects` in [root] with the executable of [MoonExecutableService].
     * Each call gets the current time limit from [timeoutMillis].
     */
    private suspend fun queryProjects(root: Path): CliResult {
        val search = project.service<MoonExecutableService>().findInWorkspace(root)
        val executable = search.executable ?: return CliResult.NotFound(search.examined)
        return MoonCli.queryProjects(executable, root, timeoutMillis())
    }

    /** Gives the time limit from [MoonSettings] in milliseconds. */
    internal fun timeoutMillis(): Int = project.service<MoonSettings>().timeoutSeconds * 1000

    /**
     * Cancels the load, waits until it stops, and sets the state to [TasksState.Loading] without the last successful load.
     * Also cancels the refresh that waits, removes the data of the last successful load from [configWatch],
     * and sets [projectDir], [queryProjects], and [refreshDelay] to their default values. Only tests use this function.
     * Call it only on the test thread.
     */
    @TestOnly
    internal suspend fun resetForTests() {
        val previous = synchronized(this) {
            projectDir = defaultProjectDir
            queryProjects = defaultQueryProjects
            refreshDelay = REFRESH_DELAY
            pending?.cancel()
            pending = null
            job.also { job = null }
        }
        previous?.cancelAndJoin()
        configWatch = ConfigWatch(null, null)
        _state.value = TasksState.Loading(null)
    }

    internal companion object {
        /** The default value of [refreshDelay]. */
        val REFRESH_DELAY: Duration = 1.seconds
    }
}
