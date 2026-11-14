package io.github.artptr.moonexplorer.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

/** Tests the mapping of the moon configuration files to the schemas, for each file type. */
class SchemaMappingTest {

    private val mapping = SchemaMapping("/ws", SchemaMapping.templateDirSet(listOf("/ws/templates/package")), SchemaSource.LOCAL)

    private fun schema(path: String) = mapping.schemaFor(path)

    @Test
    fun projectFiles() {
        assertEquals(MoonSchema.PROJECT, schema("/ws/apps/web/moon.yml"))
        assertEquals(MoonSchema.PROJECT, schema("/ws/packages/ui/icons/moon.yaml"))
        assertEquals(MoonSchema.PROJECT, schema("/ws/apps/api/moon.json"))
        assertEquals(MoonSchema.PROJECT, schema("/ws/apps/api/moon.jsonc"))
        // A project in the workspace root.
        assertEquals(MoonSchema.PROJECT, schema("/ws/moon.yml"))
    }

    @Test
    fun workspaceFiles() {
        assertEquals(MoonSchema.WORKSPACE, schema("/ws/.moon/workspace.yml"))
        assertEquals(MoonSchema.WORKSPACE, schema("/ws/.moon/workspace.json"))
        assertEquals(MoonSchema.TOOLCHAINS, schema("/ws/.moon/toolchains.yml"))
        assertEquals(MoonSchema.EXTENSIONS, schema("/ws/.moon/extensions.yaml"))
    }

    @Test
    fun tasksFiles() {
        assertEquals(MoonSchema.TASKS, schema("/ws/.moon/tasks/all.yml"))
        assertEquals(MoonSchema.TASKS, schema("/ws/.moon/tasks/node/nested.yml"))
        // A file in .moon/tasks/ has the tasks schema also if its name is moon.yml or workspace.yml.
        assertEquals(MoonSchema.TASKS, schema("/ws/.moon/tasks/moon.yml"))
        assertEquals(MoonSchema.TASKS, schema("/ws/.moon/tasks/workspace.yml"))
    }

    @Test
    fun templateFiles() {
        assertEquals(MoonSchema.TEMPLATE, schema("/ws/templates/package/template.yml"))
        assertEquals(MoonSchema.TEMPLATE, schema("/ws/templates/package/template.yaml"))
        // A template.yml outside the template directories is a file of a different tool, for example AWS SAM.
        assertNull(schema("/ws/apps/api/template.yml"))
        assertNull(schema("/ws/templates/package/nested/template.yml"))
        // A different file in a template directory gets no schema.
        assertNull(schema("/ws/templates/package/other.yml"))
        // moon keeps the templates from a remote location in .moon/cache/. The files in this directory get no schema.
        val cached = SchemaMapping("/ws", SchemaMapping.templateDirSet(listOf("/ws/.moon/cache/templates/remote")), SchemaSource.LOCAL)
        assertNull(cached.schemaFor("/ws/.moon/cache/templates/remote/template.yml"))
    }

    @Test
    fun otherFilesHaveNoSchema() {
        // moon writes the files in .moon/cache/.
        assertNull(schema("/ws/.moon/cache/schemas/project.json"))
        assertNull(schema("/ws/.moon/cache/states/moon.json"))
        // Unknown files in .moon/ and files in other subdirectories of .moon/.
        assertNull(schema("/ws/.moon/other.yml"))
        assertNull(schema("/ws/.moon/docker/workspace.yml"))
        // The IDE does not examine these formats with a JSON schema.
        assertNull(schema("/ws/apps/web/moon.toml"))
        assertNull(schema("/ws/.moon/workspace.pkl"))
        assertNull(schema("/ws/.moon/tasks/all.hcl"))
        // Names that are not moon configuration files.
        assertNull(schema("/ws/apps/web/package.json"))
        assertNull(schema("/ws/apps/web/moonx.yml"))
        assertNull(schema("/ws/apps/web/.yml"))
        assertNull(schema("/ws/apps/web/moon.yml.bak"))
        // Files outside the workspace root, also in a directory with a name that starts with the name of the root.
        assertNull(schema("/other/moon.yml"))
        assertNull(schema("/ws2/moon.yml"))
        assertNull(schema("/ws2/.moon/workspace.yml"))
    }

    @Test
    fun rootDirectoryOfTheFileSystem() {
        val root = SchemaMapping("/", emptySet(), SchemaSource.LOCAL)
        assertEquals(MoonSchema.WORKSPACE, root.schemaFor("/.moon/workspace.yml"))
        assertEquals(MoonSchema.PROJECT, root.schemaFor("/apps/web/moon.yml"))
        assertEquals("/.moon/cache/schemas", root.schemasDir)
    }

    @Test
    fun localSchemaPaths() {
        assertEquals("/ws/.moon/cache/schemas", mapping.schemasDir)
        assertEquals("/ws/.moon/cache/schemas/project.json", mapping.localSchemaPath(MoonSchema.PROJECT))
        assertEquals("/ws/.moon/cache/schemas/template.json", mapping.localSchemaPath(MoonSchema.TEMPLATE))
    }

    @Test
    fun remoteUrls() {
        assertEquals("https://moonrepo.dev/schemas/v2/tasks.json", MoonSchema.TASKS.remoteUrl)
        assertEquals("https://moonrepo.dev/schemas/v2/toolchains.json", MoonSchema.TOOLCHAINS.remoteUrl)
    }

    @Test
    fun mappingFromPaths() {
        val root = Path.of("/ws").toAbsolutePath()
        val made = SchemaMapping.of(root, listOf(root.resolve("templates/package")))
        assertEquals(MoonSchema.TEMPLATE, made.schemaFor(made.root + "/templates/package/template.yml"))
        assertEquals(SchemaSource.LOCAL, made.source)
    }
}
