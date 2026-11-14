package io.github.artptr.moonexplorer.json

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.loadJson
import io.github.artptr.moonexplorer.schema.*
import io.github.artptr.moonexplorer.testData.TestData
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText

/**
 * Tests the JSON schemas of the moon configuration files with the JSON schema service of the IDE and the real daemon.
 * The workspace is a copy of the test workspace in a temporary directory, with the recorded schemas in `.moon/cache/schemas/`.
 * The copy also has the files that the test workspace does not have: `.moon/toolchains.yml`, `.moon/extensions.yml`, and a file in a subdirectory of `.moon/tasks/`.
 */
class MoonJsonSchemaIntegrationTest : MoonTasksServiceTestCase() {

    private lateinit var root: Path
    private lateinit var schemas: MoonSchemaService

    override fun setUp() {
        super.setUp()
        // Use the real path. Thus the paths of the VFS and of the service are the same also if the temporary directory is a symbolic link.
        root = FileUtil.createTempDirectory("moon-json-schemas", null, true).toPath().toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        service.projectDir = { root }
        schemas = project.service<MoonSchemaService>()
        schemas.templates = { CliResult.Success(recordedTemplates(it)) }
        myFixture.enableInspections(YamlJsonSchemaHighlightingInspection())
    }

    override fun tearDown() {
        try {
            // Close the files before the reset. Thus the IDE does not examine them again after the deletion of the workspace.
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        } finally {
            super.tearDown()
        }
    }

    /** Copies the workspace, adds [extraFiles], loads the tasks, and waits for the mapping with the template directory. */
    private fun prepareWorkspace(extraFiles: Map<String, String> = EXTRA_FILES, schemaFiles: Boolean = true): SchemaMapping {
        copyWorkspace(root, schemaFiles)
        extraFiles.forEach { (path, text) -> root.resolve(path).createParentDirectories().writeText(text) }
        val rootFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)!!.also { it.refresh(false, true) }
        // In the IDE, the workspace is in the content of the IDE project. The daemon does not show the schema warnings in other files.
        PsiTestUtil.addContentRoot(module, rootFile)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeContentEntry(module, rootFile) }
        schemas.start()
        service.loadJson(TestData.recorded("query-projects.json"), root)
        return schemas.awaitMapping { it?.templateDirs?.isNotEmpty() == true || it?.source == SchemaSource.REMOTE }!!
    }

    private fun file(path: String): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve(path)) ?: error("No file $path")

    private fun schemaFilesFor(path: String): List<String> =
        JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file(path)).map { it.path }

    fun testEachConfigurationFileGetsItsSchema() {
        val mapping = prepareWorkspace()
        val expected = mapOf(
            ".moon/workspace.yml" to MoonSchema.WORKSPACE,
            ".moon/toolchains.yml" to MoonSchema.TOOLCHAINS,
            ".moon/extensions.yml" to MoonSchema.EXTENSIONS,
            ".moon/tasks/all.yml" to MoonSchema.TASKS,
            ".moon/tasks/node/nested.yml" to MoonSchema.TASKS,
            "templates/package/template.yml" to MoonSchema.TEMPLATE,
        ) + projectFiles().associateWith { MoonSchema.PROJECT }
        for ((path, schema) in expected) {
            assertEquals(path, listOf(mapping.localSchemaPath(schema)), schemaFilesFor(path))
        }
    }

    fun testOtherFilesGetNoMoonSchema() {
        val mapping = prepareWorkspace(EXTRA_FILES + mapOf("apps/api/template.yml" to "Resources: {}\n", "apps/web/config.yml" to "a: 1\n"))
        for (path in listOf("apps/api/template.yml", "apps/web/config.yml")) {
            val moonSchemas = schemaFilesFor(path).filter { it.startsWith(mapping.schemasDir) }
            assertEquals(path, emptyList<String>(), moonSchemas)
        }
    }

    fun testIncorrectKeysHaveWarnings() {
        prepareWorkspace()
        val files = mapOf(
            "apps/web/moon.yml" to "badProjectKey: true\ntasks:\n  build:\n    command: 'echo'\n    badTaskKey: 1\n",
            ".moon/workspace.yml" to "projects:\n  - 'apps/*'\nbadWorkspaceKey: true\n",
            ".moon/tasks/all.yml" to "badTasksKey: true\n",
            ".moon/tasks/node/nested.yml" to "badNestedKey: true\n",
            "templates/package/template.yml" to "title: 'Package'\ndescription: 'A package.'\nbadTemplateKey: true\n",
        )
        for ((path, text) in files) {
            val keys = Regex("""(bad\w+)""").findAll(text).map { it.value }.toList()
            assertEquals(path, keys, warningsIn(path, text).filter { it.startsWith("bad") })
        }
    }

    fun testIncorrectValuesHaveWarningsInToolchainsAndExtensions() {
        prepareWorkspace()
        // The schemas accept all keys as toolchain IDs or extension IDs. Thus only a value with an incorrect type gives a warning.
        assertEquals(listOf("true"), warningsIn(".moon/toolchains.yml", "badToolchainsKey: true\notherToolchain: {}\n"))
        assertEquals(listOf("true"), warningsIn(".moon/extensions.yml", "badExtensionsKey: true\notherExtension: {}\n"))
    }

    fun testCompletionShowsTheKeys() {
        prepareWorkspace()
        assertContainsElements(completionAt("apps/web/moon.yml", "language: 'typescript'\n<caret>\n"), "dependsOn", "tasks", "toolchains")
        assertContainsElements(completionAt(".moon/workspace.yml", "<caret>\n"), "projects", "vcs")
        assertContainsElements(completionAt(".moon/tasks/all.yml", "<caret>\n"), "fileGroups", "tasks", "taskOptions")
        assertContainsElements(completionAt("templates/package/template.yml", "<caret>\n"), "title", "description", "variables")
        // The toolchain IDs and the extension IDs are not fixed. Thus the completion shows only the fixed root keys.
        assertContainsElements(completionAt(".moon/toolchains.yml", "<caret>\n"), "extends")
        assertContainsElements(completionAt(".moon/extensions.yml", "<caret>\n"), "extends")
    }

    fun testJsonFilesGetSchemasAndWarnings() {
        myFixture.enableInspections(JsonSchemaComplianceInspection())
        val mapping = prepareWorkspace(EXTRA_FILES + mapOf("apps/api/moon.json" to "{}\n", ".moon/tasks/extra.jsonc" to "{}\n"))
        assertEquals(listOf(mapping.localSchemaPath(MoonSchema.PROJECT)), schemaFilesFor("apps/api/moon.json"))
        assertEquals(listOf(mapping.localSchemaPath(MoonSchema.TASKS)), schemaFilesFor(".moon/tasks/extra.jsonc"))
        assertEquals(listOf("\"badProjectKey\""), jsonWarningsIn("apps/api/moon.json", "{\n  \"badProjectKey\": true\n}\n"))
        assertEquals(listOf("\"badTasksKey\""), jsonWarningsIn(".moon/tasks/extra.jsonc", "{\n  // A comment.\n  \"badTasksKey\": true\n}\n"))
    }

    fun testFailedSyncUsesTheRemoteSchemas() {
        schemas.syncSchemas = { CliResult.Failed("moon failed") }
        prepareWorkspace(schemaFiles = false)
        val providers = MoonJsonSchemaProviderFactory().getProviders(project).filterIsInstance<MoonJsonSchemaFileProvider>()
        val provider = providers.single { it.schema == MoonSchema.PROJECT }
        assertTrue(provider.isAvailable(file("apps/web/moon.yml")))
        assertEquals(SchemaType.remoteSchema, provider.schemaType)
        assertEquals("https://moonrepo.dev/schemas/v2/project.json", provider.remoteSource)
        // The JSON schema service gives the file of the URL, as for a URL in the user mappings. The JSON plugin downloads the file only if
        // the IDE lets it download remote schemas. In the tests, the JSON plugin does not download files.
        val files = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file("apps/web/moon.yml"))
        assertEquals(listOf("https://moonrepo.dev/schemas/v2/project.json"), files.map { it.url })
    }

    fun testFactoryIsRegistered() {
        assertTrue(JsonSchemaProviderFactory.EP_NAME.extensionList.any { it is MoonJsonSchemaProviderFactory })
    }

    /** Gives the path of each `moon.yml` in the workspace, from the root. */
    private fun projectFiles(): List<String> = Files.walk(root).use { paths ->
        paths.filter { it.fileName.toString() == "moon.yml" }.map { root.relativize(it).invariantSeparatorsPathString }.toList()
    }.also { assertTrue("The workspace has no moon.yml", it.size >= 9) }

    /** Writes [text] to [path] and refreshes the file in the VFS. The VFS keeps the old content of a file that it knows. */
    private fun write(path: String, text: String): VirtualFile {
        root.resolve(path).writeText(text)
        return file(path).also { it.refresh(false, false) }
    }

    /** Writes [text] to [path], opens the file, and gives the text of each warning of the schema inspection. */
    private fun warningsIn(path: String, text: String): List<String> {
        myFixture.configureFromExistingVirtualFile(write(path, text))
        return myFixture.doHighlighting(HighlightSeverity.WARNING).filter { it.inspectionToolId == "YAMLSchemaValidation" }.map { it.text }
    }

    /** Writes [text] to [path], opens the file, and gives the text of each warning of the JSON schema inspection. */
    private fun jsonWarningsIn(path: String, text: String): List<String> {
        myFixture.configureFromExistingVirtualFile(write(path, text))
        return myFixture.doHighlighting(HighlightSeverity.WARNING).filter { it.inspectionToolId == "JsonSchemaCompliance" }.map { it.text }
    }

    /** Writes [text] to [path], puts the caret at `<caret>`, and gives the strings of the completion. */
    private fun completionAt(path: String, text: String): List<String> {
        val caret = text.indexOf("<caret>")
        myFixture.configureFromExistingVirtualFile(write(path, text.replace("<caret>", "")))
        myFixture.editor.caretModel.moveToOffset(caret)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private companion object {
        val EXTRA_FILES = mapOf(
            ".moon/toolchains.yml" to "{}\n",
            ".moon/extensions.yml" to "{}\n",
            ".moon/tasks/node/nested.yml" to "tasks: {}\n",
        )
    }
}
