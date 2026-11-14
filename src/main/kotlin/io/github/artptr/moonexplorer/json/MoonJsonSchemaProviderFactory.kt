package io.github.artptr.moonexplorer.json

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.schema.MoonSchema
import io.github.artptr.moonexplorer.schema.MoonSchemaService
import io.github.artptr.moonexplorer.schema.SchemaSource

/**
 * Gives one JSON schema provider for each [MoonSchema]. The providers use the data of [MoonSchemaService]. Refer to ADR 0006.
 * `moon-explorer-json.xml` registers the factory. Thus the IDE uses it only when the JSON plugin is active.
 */
class MoonJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        val service = project.service<MoonSchemaService>()
        return MoonSchema.entries.map { MoonJsonSchemaFileProvider(service, it) }
    }
}

/**
 * Gives [schema] to the moon configuration files of one type. The provider reads the current mapping for each call.
 * Thus a new workspace root or a new template directory does not make a new provider. [MoonSchemaResetter] tells the IDE about a change.
 */
class MoonJsonSchemaFileProvider(private val service: MoonSchemaService, val schema: MoonSchema) : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean {
        // moon reads only the local disk. A file in an archive or in a different file system is not a configuration file of the workspace.
        if (!file.isInLocalFileSystem) return false
        return service.mapping.value?.schemaFor(file.path) == schema
    }

    override fun getName(): String = MoonExplorerBundle.message(schema.nameKey)

    /** Gives the local schema file, or the file of the remote URL. Gives null if the local file does not exist yet. */
    override fun getSchemaFile(): VirtualFile? {
        val mapping = service.mapping.value ?: return null
        return when (mapping.source) {
            SchemaSource.LOCAL -> LocalFileSystem.getInstance().findFileByPath(mapping.localSchemaPath(schema))
            SchemaSource.REMOTE -> JsonFileResolver.urlToFile(schema.remoteUrl)
        }
    }

    override fun getSchemaType(): SchemaType = when (service.mapping.value?.source) {
        SchemaSource.REMOTE -> SchemaType.remoteSchema
        else -> SchemaType.schema
    }

    /** moon writes the schemas with draft 7, `http://json-schema.org/draft-07/schema#`. */
    override fun getSchemaVersion(): JsonSchemaVersion = JsonSchemaVersion.SCHEMA_7

    override fun getRemoteSource(): String? = if (service.mapping.value?.source == SchemaSource.REMOTE) schema.remoteUrl else null
}
