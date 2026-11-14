package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.loadJson
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.vfsPath
import io.github.artptr.moonexplorer.testData.TestData
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
        assertEquals(1, syncCalls.get())
    }

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
