package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitWithTimeout
import io.github.artptr.moonexplorer.model.loadJson
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.vfsPath
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** Tests [MoonSchemaService] with a copy of the test workspace in a temporary directory. The fakes give the output of moon. */
class MoonSchemaServiceTest : MoonTasksServiceTestCase() {

    private lateinit var root: Path
    private lateinit var schemas: MoonSchemaService
    private val messages = AtomicInteger()
    private val syncCalls = AtomicInteger()
    private val templateCalls = AtomicInteger()

    override fun setUp() {
        super.setUp()
        // Use the real path. Thus the paths of the VFS and of the service are the same also if the temporary directory is a symbolic link.
        root = FileUtil.createTempDirectory("moon-schemas", null, true).toPath().toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        service.projectDir = { root }
        schemas = project.service<MoonSchemaService>()
        schemas.templates = { templateCalls.incrementAndGet(); CliResult.Success(recordedTemplates(it)) }
        project.messageBus.connect(testRootDisposable).subscribe(MoonSchemaListener.TOPIC, MoonSchemaListener { messages.incrementAndGet() })
    }

    private fun load() {
        schemas.start()
        service.loadJson(TestData.recorded("query-projects.json"), root)
    }

    fun testLocalSchemasAndTemplates() {
        copyWorkspace(root)
        schemas.syncSchemas = { syncCalls.incrementAndGet(); CliResult.Success("") }
        load()
        val mapping = schemas.awaitMapping { it?.templateDirs?.isNotEmpty() == true }!!
        assertEquals(vfsPath(root), mapping.root)
        assertEquals(SchemaSource.LOCAL, mapping.source)
        assertEquals(MoonSchema.TEMPLATE, mapping.schemaFor("${mapping.root}/templates/package/template.yml"))
        // The schema files exist. Thus the service does not start moon sync.
        assertEquals(0, syncCalls.get())
        // The service adds the task graph with a second Loaded state. The service runs moon templates only one time.
        assertEquals(1, templateCalls.get())
        // One message for the root, and one for the templates.
        assertEquals(2, messages.get())
    }

    fun testSyncWritesMissingSchemas() {
        copyWorkspace(root, schemas = false)
        schemas.syncSchemas = { syncCalls.incrementAndGet(); copyRecordedSchemas(it); CliResult.Success("") }
        load()
        val mapping = schemas.awaitMapping { it?.templateDirs?.isNotEmpty() == true }!!
        assertEquals(SchemaSource.LOCAL, mapping.source)
        assertEquals(1, syncCalls.get())
        // The service refreshed the VFS. Thus the provider finds the new files without a refresh.
        assertNotNull(LocalFileSystem.getInstance().findFileByPath(mapping.localSchemaPath(MoonSchema.PROJECT)))
        // One message for the root, one for the templates, and one for the new files.
        assertEquals(3, messages.get())
    }

    fun testFailedSyncUsesRemoteSchemas() {
        copyWorkspace(root, schemas = false)
        schemas.syncSchemas = { syncCalls.incrementAndGet(); CliResult.Failed("moon failed") }
        load()
        assertEquals(SchemaSource.REMOTE, schemas.awaitMapping { it?.source == SchemaSource.REMOTE }!!.source)
        // The service runs moon sync for the new root. It can run it again for the load, if the first run completed before the load.
        assertTrue(syncCalls.get() in 1..2)
    }

    fun testNewLoadUsesLocalSchemasAgainAfterRemoteFallback() {
        copyWorkspace(root, schemas = false)
        schemas.syncSchemas = { syncCalls.incrementAndGet(); CliResult.Failed("moon failed") }
        val loaded = TestData.loadedState(TestData.recordedTasks(), root)
        schemas.update(loaded, root)
        schemas.awaitMapping { it?.source == SchemaSource.REMOTE }
        // moon writes the files later, for example in moon run. The next load finds them, also if moon sync continues to fail.
        copyRecordedSchemas(root)
        schemas.update(TasksState.Loading(loaded), root)
        schemas.update(loaded, root)
        val mapping = schemas.awaitMapping { it?.source == SchemaSource.LOCAL }!!
        assertNotNull(LocalFileSystem.getInstance().findFileByPath(mapping.localSchemaPath(MoonSchema.PROJECT)))
    }

    fun testNewLoadRunsMoonSyncAgainAfterRemoteFallback() {
        copyWorkspace(root, schemas = false)
        schemas.syncSchemas = { if (syncCalls.incrementAndGet() == 1) CliResult.Failed("moon failed") else { copyRecordedSchemas(it); CliResult.Success("") } }
        val loaded = TestData.loadedState(TestData.recordedTasks(), root)
        schemas.update(loaded, root)
        schemas.awaitMapping { it?.source == SchemaSource.REMOTE }
        schemas.update(TasksState.Loading(loaded), root)
        schemas.update(loaded, root)
        assertEquals(SchemaSource.LOCAL, schemas.awaitMapping { it?.source == SchemaSource.LOCAL }!!.source)
        assertEquals(2, syncCalls.get())
    }

    fun testNewRootAfterLoadedStateLoadsTemplates() {
        // A StateFlow can skip the Loading state. Then the service gets two Loaded states with different roots, one after the other.
        val first = root.resolve("first").also { copyWorkspace(it) }
        val second = root.resolve("second").also { copyWorkspace(it) }
        schemas.update(TestData.loadedState(TestData.recordedTasks(), first), first)
        schemas.awaitMapping { it?.templateDirs?.contains(vfsPath(first.resolve("templates/package"))) == true }
        schemas.update(TestData.loadedState(TestData.recordedTasks(), second), second)
        val mapping = schemas.awaitMapping { it?.root == vfsPath(second) && it.templateDirs.isNotEmpty() }!!
        assertEquals(setOf(vfsPath(second.resolve("templates/package"))), mapping.templateDirs.toSet())
        assertEquals(2, templateCalls.get())
    }

    fun testOnlyAnActiveJobChangesTheMapping() {
        copyWorkspace(root)
        // A Loading state gives the root, but does not start moon templates. The schema files exist. Thus the sync job stops without a change.
        schemas.update(TasksState.Loading(null), root)
        PlatformTestUtil.waitWithEventsDispatching("The jobs of the schema service did not stop", schemas::jobsCompletedForTests, 10)
        val sent = messages.get()
        val toRemote = { mapping: SchemaMapping -> mapping.copy(source = SchemaSource.REMOTE) }
        // A cancelled job, for example a job of an old load.
        schemas.changeMapping(root, Job().apply { cancel() }, always = true, toRemote)
        // An active job for a different root.
        val job = Job()
        try {
            schemas.changeMapping(root.resolve("other"), job, always = true, toRemote)
            assertEquals(SchemaSource.LOCAL, schemas.mapping.value!!.source)
            assertEquals(sent, messages.get())
            schemas.changeMapping(root, job, change = toRemote)
            assertEquals(SchemaSource.REMOTE, schemas.mapping.value!!.source)
            assertEquals(sent + 1, messages.get())
        } finally {
            job.cancel()
        }
    }

    fun testOldSyncJobDoesNotChangeTheMappingOfANewRoot() {
        val first = root.resolve("first").also { copyWorkspace(it, schemas = false) }
        val second = root.resolve("second").also { copyWorkspace(it) }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldJobDone = CompletableDeferred<Unit>()
        schemas.syncSchemas = {
            syncCalls.incrementAndGet()
            started.complete(Unit)
            // A moon process that fails after the cancellation of its job.
            try {
                withContext(NonCancellable) { release.await() }
                CliResult.Failed("moon failed")
            } finally {
                oldJobDone.complete(Unit)
            }
        }
        schemas.update(TasksState.Loading(null), first)
        started.awaitWithTimeout()
        schemas.update(TasksState.Loading(null), second)
        release.complete(Unit)
        oldJobDone.awaitWithTimeout()
        PlatformTestUtil.waitWithEventsDispatching("The jobs of the schema service did not stop", schemas::jobsCompletedForTests, 10)
        val mapping = schemas.mapping.value!!
        assertEquals(vfsPath(second), mapping.root)
        assertEquals(SchemaSource.LOCAL, mapping.source)
        assertEquals(1, syncCalls.get())
    }

    fun testOldTemplatesJobDoesNotChangeTheMapping() {
        copyWorkspace(root)
        val release = CompletableDeferred<Unit>()
        val oldJobDone = CompletableDeferred<Unit>()
        schemas.templates = {
            if (templateCalls.incrementAndGet() == 1) {
                // A moon process that completes after the cancellation of its job. The cancellation stops the job at the end of withContext.
                // testOnlyAnActiveJobChangesTheMapping does a check of a cancelled job that did not stop.
                try {
                    withContext(NonCancellable) { release.await() }
                    CliResult.Success("""{"old":{"root":${quote(root.resolve("old").toString())}}}""")
                } finally {
                    oldJobDone.complete(Unit)
                }
            } else {
                CliResult.Success(recordedTemplates(it))
            }
        }
        val loaded = TestData.loadedState(TestData.recordedTasks(), root)
        schemas.update(loaded, root)
        schemas.update(TasksState.Loading(loaded), root)
        schemas.update(loaded, root)
        val expected = setOf(vfsPath(root.resolve("templates/package")))
        schemas.awaitMapping { it?.templateDirs?.toSet() == expected }
        release.complete(Unit)
        oldJobDone.awaitWithTimeout()
        assertEquals(expected, schemas.awaitMapping { true }!!.templateDirs.toSet())
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\") + "\""

    fun testSyncWithoutAllFilesUsesRemoteSchemas() {
        copyWorkspace(root, schemas = false)
        // moon completes, but writes no files. For example, the moon version is older and has other schema files.
        schemas.syncSchemas = { CliResult.Success("") }
        load()
        assertEquals(SchemaSource.REMOTE, schemas.awaitMapping { it?.source == SchemaSource.REMOTE }!!.source)
    }

    fun testFailedTemplatesGiveNoTemplateDirectories() {
        copyWorkspace(root)
        schemas.templates = { templateCalls.incrementAndGet(); CliResult.Failed("moon failed") }
        load()
        val mapping = schemas.awaitMapping { it != null }!!
        assertEquals(1, templateCalls.get())
        assertEquals(emptySet<String>(), mapping.templateDirs)
        assertNull(mapping.schemaFor("${mapping.root}/templates/package/template.yml"))
    }

    fun testNoWorkspaceHasNoMapping() {
        copyWorkspace(root)
        load()
        schemas.awaitMapping { it?.templateDirs?.isNotEmpty() == true }
        service.loadNoWorkspace()
        assertNull(schemas.awaitMapping { it == null })
    }

    fun testStartTwiceStartsOneCollector() {
        copyWorkspace(root)
        schemas.start()
        load()
        schemas.awaitMapping { it?.templateDirs?.isNotEmpty() == true }
        assertEquals(1, templateCalls.get())
    }
}
