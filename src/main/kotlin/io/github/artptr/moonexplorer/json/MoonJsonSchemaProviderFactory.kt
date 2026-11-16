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
import io.github.artptr.moonexplorer.schema.SchemaMapping
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
 * How the JSON plugin of the IDE gets the remote schema of a provider. The JSON plugin changed this procedure in the 2026.3 release.
 * In each procedure, the JSON plugin downloads the schema only if the IDE can download remote schemas.
 */
enum class RemoteSchemaPath {
    /**
     * The old procedure: 2026.2 and earlier, and the 2026.3 EAP build 263.7031.23. The provider gives the file of the URL from [JsonFileResolver.urlToFile].
     * Before the JSON plugin reads this file, it calls `JsonFileResolver.startFetchingHttpFileIfNeeded`. This function starts the download.
     * If the provider gives no file, the provider gets no schema.
     */
    SCHEMA_FILE,

    /**
     * The new procedure: the 2026.3 release (build 263.7307.44) and later. The provider gives no file.
     * Then the JSON plugin gets the URL from [JsonSchemaFileProvider.getRemoteSource] and downloads it with `JsonSchemaRemoteContentService`.
     * If the provider gives the file of the URL, the JSON plugin does not download it.
     */
    REMOTE_SOURCE;

    companion object {
        /**
         * The function of [JsonFileResolver] that starts the download in the old procedure. Only the IDE builds with the old procedure have this function.
         * The procedure changed in a build after 263.7031.23. Thus the branch number of the build does not identify the procedure.
         */
        const val START_FETCHING = "startFetchingHttpFileIfNeeded"

        /**
         * Gives the procedure of the JSON plugin with the class [resolver], for example [JsonFileResolver].
         * The old procedure operates only if [resolver] has the function [START_FETCHING]. Thus without this function, the result is [REMOTE_SOURCE].
         */
        fun detect(resolver: Class<*>): RemoteSchemaPath = if (resolver.methods.any { it.name == START_FETCHING }) SCHEMA_FILE else REMOTE_SOURCE

        /** The procedure of this IDE. */
        val current: RemoteSchemaPath by lazy { detect(JsonFileResolver::class.java) }
    }
}

/**
 * Gives [schema] to the moon configuration files of one type. The provider reads the current mapping for each call.
 * Thus a new workspace root or a new template directory does not make a new provider. [MoonSchemaResetter] tells the IDE about a change.
 * [remotePath] is the procedure for the remote schemas. Tests change it.
 */
class MoonJsonSchemaFileProvider(
    private val service: MoonSchemaService,
    private val schema: MoonSchema,
    private val remotePath: RemoteSchemaPath = RemoteSchemaPath.current,
) : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean {
        // moon reads only the local disk. A file in an archive or in a different file system is not a configuration file of the workspace.
        if (!file.isInLocalFileSystem) return false
        // The IDE calls each provider for each file. The name check is fast and does not make the path. Most files stop here.
        if (!SchemaMapping.isSchemaFileName(file.nameSequence)) return false
        return service.mapping.value?.schemaFor(file.path) == schema
    }

    override fun getName(): String = MoonExplorerBundle.message(schema.nameKey)

    /**
     * Gives the local schema file. Gives null if the local file does not exist yet.
     * For a remote schema, the result depends on [remotePath]. Refer to [RemoteSchemaPath].
     */
    override fun getSchemaFile(): VirtualFile? {
        val mapping = service.mapping.value ?: return null
        return when (mapping.source) {
            SchemaSource.LOCAL -> LocalFileSystem.getInstance().findFileByPath(mapping.localSchemaPath(schema))
            SchemaSource.REMOTE -> when (remotePath) {
                RemoteSchemaPath.SCHEMA_FILE -> JsonFileResolver.urlToFile(schema.remoteUrl)
                RemoteSchemaPath.REMOTE_SOURCE -> null
            }
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
