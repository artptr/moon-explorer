package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.model.MoonOutputException
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume

private val LOG = logger<MoonSchemaService>()

/**
 * Keeps the data for the JSON schemas of the moon configuration files. Refer to ADR 0006.
 * The service monitors the state of [MoonTasksService]:
 *
 * 1. When the workspace root changes, the service makes a new [SchemaMapping] for the root.
 * 2. For a new root and after each new load, the service examines `.moon/cache/schemas/`. If a schema file does not exist,
 *    the service runs `moon sync config-schemas --force`. If moon fails, the mapping uses the schemas on the moon website ([SchemaSource.REMOTE]).
 *    When the files exist again, the mapping uses them ([SchemaSource.LOCAL]).
 * 3. After each new load, the service runs `moon templates --json` and adds the template directories to the mapping.
 *
 * After each change, the service sends [MoonSchemaListener.schemasChanged]. The service does not use the classes of the JSON plugin.
 * [project] is the IDE project of the service. [scope] is the coroutine scope of the service.
 */
@Service(Service.Level.PROJECT)
class MoonSchemaService(private val project: Project, private val scope: CoroutineScope) {

    private val _mapping = MutableStateFlow<SchemaMapping?>(null)

    /** The current mapping, or null if the IDE project has no moon workspace. The JSON schema provider reads this value for each file. */
    val mapping: StateFlow<SchemaMapping?> = _mapping.asStateFlow()

    private val defaultTemplates: suspend (Path) -> CliResult = { project.service<MoonTasksService>().runMoon(it, MoonCli::templates) }
    private val defaultSyncSchemas: suspend (Path) -> CliResult = { project.service<MoonTasksService>().runMoon(it, MoonCli::syncConfigSchemas) }

    /**
     * Runs `moon templates --json` in a workspace root. Tests change this value to a fake.
     * Change it only on the test thread, before you call [start].
     */
    internal var templates: suspend (Path) -> CliResult = defaultTemplates

    /**
     * Runs `moon sync config-schemas --force` in a workspace root. Tests change this value to a fake.
     * Change it only on the test thread, before you call [start].
     */
    internal var syncSchemas: suspend (Path) -> CliResult = defaultSyncSchemas

    // The fields below change only with the lock of the service.
    private var collector: Job? = null
    private var templatesJob: Job? = null
    private var syncJob: Job? = null

    /** The workspace root of [mapping]. */
    private var root: Path? = null

    /** The last state that the service examined. The service runs `moon templates` only for a new load. */
    private var lastState: TasksState? = null

    /** Starts to monitor the state of [MoonTasksService]. A second call does nothing. */
    @Synchronized
    fun start() {
        if (collector != null) return
        val tasks = project.service<MoonTasksService>()
        collector = scope.launch {
            // The service sets the watch of the configuration before it publishes the state. Thus the root agrees with the state.
            tasks.state.collect { update(it, tasks.configWatch.root) }
        }
    }

    /**
     * Examines the next [state] of [MoonTasksService] for the workspace root [newRoot]. The collector of [start] calls this function.
     * Tests call it directly to give a sequence of states.
     */
    internal fun update(state: TasksState, newRoot: Path?) {
        val changed = synchronized(this) {
            val previous = lastState
            lastState = state
            var changed = false
            val rootChanged = newRoot != root
            if (rootChanged) {
                root = newRoot
                templatesJob?.cancel()
                templatesJob = null
                syncJob?.cancel()
                syncJob = null
                changed = setMapping(newRoot?.let { SchemaMapping.of(it) })
            }
            if (newRoot != null) {
                // If moon task-graph completes after the tasks, the tasks service adds the task graph with a second Loaded state.
                // This state has the same tasks, thus it is not a new load.
                // A StateFlow can skip a state. Thus a new root or new tasks after a Loaded state is also a new load.
                val newLoad = state is TasksState.Loaded &&
                    (rootChanged || previous !is TasksState.Loaded || previous.tasks !== state.tasks)
                // Examine the schema files for a new root, also if the load fails. Examine them again after each new load.
                // Thus the mapping uses the local files again after moon wrote them, for example after a failed sync.
                if ((rootChanged || newLoad) && syncJob?.isActive != true) {
                    syncJob = scope.launch { syncLocalSchemas(newRoot) }
                }
                if (newLoad) {
                    // Cancel the old job with the lock. Thus the old job cannot change the mapping after this point. Refer to changeMapping.
                    templatesJob?.cancel()
                    templatesJob = scope.launch { loadTemplates(newRoot) }
                }
            }
            changed
        }
        if (changed) publish()
    }

    /** Sets [mapping]. Gives true if the value changed. Call this function only with the lock of the service. */
    private fun setMapping(value: SchemaMapping?): Boolean {
        if (_mapping.value == value) return false
        _mapping.value = value
        return true
    }

    /**
     * Changes the mapping with [change] if the mapping is for [root] and [job] is active. A different root means that a new workspace replaced it.
     * The service cancels a job with the lock before it starts the next job of the same type. Thus only the last job of each type changes the mapping,
     * also if a moon process completes after the cancellation of its job. If [always] is true, the function sends the message also if the mapping did not change.
     * [job] is the job that calls the function. Tests call the function directly.
     */
    internal fun changeMapping(root: Path, job: Job, always: Boolean = false, change: (SchemaMapping) -> SchemaMapping) {
        val changed = synchronized(this) {
            val current = _mapping.value
            if (this.root != root || current == null || !job.isActive) return
            setMapping(change(current)) || always
        }
        if (changed) publish()
    }

    /**
     * Sends the message outside the lock of the service. The listeners can call other services.
     * An exception in a listener does not stop the collector of [start].
     */
    private fun publish() {
        if (project.isDisposed) return
        try {
            project.messageBus.syncPublisher(MoonSchemaListener.TOPIC).schemasChanged()
        } catch (e: Exception) {
            if (e is ControlFlowException || e is CancellationException) throw e
            LOG.error("A listener of the moon schemas failed", e)
        }
    }

    /**
     * Makes sure that `.moon/cache/schemas/` in [root] contains the schema files. If a file does not exist, runs `moon sync config-schemas --force`.
     * If moon fails, the mapping uses [SchemaSource.REMOTE]. If the files exist, the mapping uses [SchemaSource.LOCAL].
     */
    private suspend fun syncLocalSchemas(root: Path) {
        val job = currentCoroutineContext().job
        val dir = SchemaMapping.of(root).schemasDir.let(Path::of)
        val sync = withContext(Dispatchers.IO) {
            if (hasAllSchemas(dir)) return@withContext SyncResult.EXISTED
            val result = syncSchemas(root)
            when {
                result !is CliResult.Success -> SyncResult.FAILED.also { LOG.info("moon sync config-schemas failed in $root: $result") }
                hasAllSchemas(dir) -> SyncResult.WRITTEN
                else -> SyncResult.FAILED.also { LOG.info("moon sync config-schemas did not write all schema files to $dir") }
            }
        }
        if (sync == SyncResult.FAILED) {
            changeMapping(root, job) { it.copy(source = SchemaSource.REMOTE) }
            return
        }
        // The files existed before, and the mapping already uses them.
        if (sync == SyncResult.EXISTED && mapping.value?.source == SchemaSource.LOCAL) return
        // The VFS can have old data about the directory. The JSON schema provider finds the files in the VFS.
        // The refresh is asynchronous. A synchronous refresh on this thread waits for the EDT.
        suspendCancellableCoroutine { continuation ->
            LocalFileSystem.getInstance().refreshNioFiles(listOf(dir), true, true) { continuation.resume(Unit) }
        }
        // If the mapping did not change, the files are new. Thus send the message also if the mapping is the same.
        changeMapping(root, job, always = true) { it.copy(source = SchemaSource.LOCAL) }
    }

    private enum class SyncResult { EXISTED, WRITTEN, FAILED }

    private fun hasAllSchemas(dir: Path): Boolean = MoonSchema.entries.all { Files.isRegularFile(dir.resolve(it.fileName)) }

    /**
     * Runs `moon templates --json` in [root] and sets the template directories of the mapping. If moon fails, the mapping keeps its directories.
     */
    private suspend fun loadTemplates(root: Path) {
        val job = currentCoroutineContext().job
        val dirs = withContext(Dispatchers.IO) {
            when (val result = templates(root)) {
                is CliResult.Success -> try {
                    TemplatesParser.parse(result.stdout)
                } catch (e: MoonOutputException) {
                    LOG.info("Cannot read the output of moon templates in $root", e)
                    null
                }
                else -> {
                    LOG.info("moon templates failed in $root: $result")
                    null
                }
            }
        } ?: return
        val templateDirs = SchemaMapping.templateDirsOf(dirs)
        changeMapping(root, job) { it.copy(templateDirs = templateDirs) }
    }

    /**
     * Stops the service and waits until its jobs stop. Removes the mapping and sets [templates] and [syncSchemas] to their default values.
     * Only tests use this function. Call it only on the test thread. After this function, [start] starts the service again.
     */
    @TestOnly
    internal suspend fun resetForTests() {
        val jobs = synchronized(this) {
            templates = defaultTemplates
            syncSchemas = defaultSyncSchemas
            listOfNotNull(collector, templatesJob, syncJob).also {
                collector = null
                templatesJob = null
                syncJob = null
                root = null
                lastState = null
                _mapping.value = null
            }
        }
        jobs.forEach { it.cancelAndJoin() }
    }

    /** Gives true if the jobs that the service started stopped. Only tests use this function. */
    @TestOnly
    internal fun jobsCompletedForTests(): Boolean = synchronized(this) { listOfNotNull(templatesJob, syncJob).all { it.isCompleted } }
}
