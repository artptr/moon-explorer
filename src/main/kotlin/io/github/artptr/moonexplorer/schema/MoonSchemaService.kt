package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.cli.MoonCli
import io.github.artptr.moonexplorer.model.MoonOutputException
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.vfsPath
import io.github.artptr.moonexplorer.settings.MoonExecutableService
import io.github.artptr.moonexplorer.settings.MoonSettings
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
 *    If `.moon/cache/schemas/` does not contain all schema files, the service runs `moon sync config-schemas --force` one time for the root.
 *    If moon fails, the mapping uses the schemas on the moon website ([SchemaSource.REMOTE]).
 * 2. After each load that completes, the service runs `moon templates --json` and adds the template directories to the mapping.
 *
 * After each change, the service sends [MoonSchemaListener.schemasChanged]. The service does not use the classes of the JSON plugin.
 * [project] is the IDE project of the service. [scope] is the coroutine scope of the service.
 */
@Service(Service.Level.PROJECT)
class MoonSchemaService(private val project: Project, private val scope: CoroutineScope) {

    private val _mapping = MutableStateFlow<SchemaMapping?>(null)

    /** The current mapping, or null if the IDE project has no moon workspace. The JSON schema provider reads this value for each file. */
    val mapping: StateFlow<SchemaMapping?> = _mapping.asStateFlow()

    private val defaultTemplates: suspend (Path) -> CliResult = { runMoon(it, MoonCli::templates) }
    private val defaultSyncSchemas: suspend (Path) -> CliResult = { runMoon(it, MoonCli::syncConfigSchemas) }

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

    /** The last state that the service examined. The service runs `moon templates` only when the state changes to [TasksState.Loaded]. */
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

    private fun update(state: TasksState, newRoot: Path?) {
        val changed = synchronized(this) {
            val previous = lastState
            lastState = state
            var changed = false
            if (newRoot != root) {
                root = newRoot
                templatesJob?.cancel()
                syncJob?.cancel()
                changed = setMapping(newRoot?.let { SchemaMapping.of(it) })
                if (newRoot != null) syncJob = scope.launch { syncLocalSchemas(newRoot) }
            }
            // A load that completes gives Loaded. The service adds the task graph later with a second Loaded state.
            // Thus a Loaded state after a Loaded state does not apply.
            if (newRoot != null && state is TasksState.Loaded && previous !is TasksState.Loaded) {
                templatesJob?.cancel()
                templatesJob = scope.launch { loadTemplates(newRoot) }
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
     * Changes the mapping with [change] if the mapping is for [root]. A different root means that a new workspace replaced it.
     * If [always] is true, the function sends the message also if the mapping did not change.
     */
    private fun changeMapping(root: Path, always: Boolean = false, change: (SchemaMapping) -> SchemaMapping = { it }) {
        val changed = synchronized(this) {
            val current = _mapping.value
            if (this.root != root || current == null) return
            setMapping(change(current)) || always
        }
        if (changed) publish()
    }

    /** Sends the message outside the lock of the service. The listeners can call other services. */
    private fun publish() {
        if (!project.isDisposed) project.messageBus.syncPublisher(MoonSchemaListener.TOPIC).schemasChanged()
    }

    /**
     * Makes sure that `.moon/cache/schemas/` in [root] contains the schema files. If a file does not exist, runs `moon sync config-schemas --force`.
     * If moon fails, the mapping uses [SchemaSource.REMOTE].
     */
    private suspend fun syncLocalSchemas(root: Path) {
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
        when (sync) {
            // The mapping already uses the files.
            SyncResult.EXISTED -> Unit
            SyncResult.FAILED -> changeMapping(root) { it.copy(source = SchemaSource.REMOTE) }
            SyncResult.WRITTEN -> {
                // The VFS does not know the new files yet. The JSON schema provider finds the files in the VFS.
                // The refresh is asynchronous. A synchronous refresh on this thread waits for the EDT.
                suspendCancellableCoroutine { continuation ->
                    LocalFileSystem.getInstance().refreshNioFiles(listOf(dir), true, true) { continuation.resume(Unit) }
                }
                // The mapping did not change, but the files exist now. Thus send the message also if the mapping is the same.
                changeMapping(root, always = true)
            }
        }
    }

    private enum class SyncResult { EXISTED, WRITTEN, FAILED }

    private fun hasAllSchemas(dir: Path): Boolean = MoonSchema.entries.all { Files.isRegularFile(dir.resolve(it.fileName)) }

    /** Runs `moon templates --json` in [root] and sets the template directories of the mapping. If moon fails, the mapping keeps its directories. */
    private suspend fun loadTemplates(root: Path) {
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
        val templateDirs = SchemaMapping.templateDirSet(dirs.map { vfsPath(it.toAbsolutePath().normalize()) })
        changeMapping(root) { it.copy(templateDirs = templateDirs) }
    }

    /** Runs [command] in [root] with the executable of [MoonExecutableService] and the time limit of [MoonSettings]. */
    private suspend fun runMoon(root: Path, command: suspend (Path, Path, Int) -> CliResult): CliResult {
        val search = project.service<MoonExecutableService>().findInWorkspace(root)
        val executable = search.executable ?: return CliResult.NotFound(search.examined)
        return command(executable, root, project.service<MoonSettings>().timeoutSeconds * 1000)
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
