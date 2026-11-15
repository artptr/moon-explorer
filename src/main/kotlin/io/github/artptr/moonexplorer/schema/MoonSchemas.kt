package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.CollectionFactory
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.MOON_DIR
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.PROJECT_CONFIG
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.WORKSPACE_CONFIG
import io.github.artptr.moonexplorer.model.vfsPath
import java.nio.file.Path

/**
 * A JSON schema that moon writes for one type of configuration file. [id] is the base name of the schema file, for example `project` for `project.json`.
 * [nameKey] is the key of the schema name in `MoonExplorerBundle.properties`.
 */
enum class MoonSchema(val id: String, val nameKey: String) {
    PROJECT("project", "schema.name.project"),
    WORKSPACE("workspace", "schema.name.workspace"),
    TOOLCHAINS("toolchains", "schema.name.toolchains"),
    EXTENSIONS("extensions", "schema.name.extensions"),
    TASKS("tasks", "schema.name.tasks"),
    TEMPLATE("template", "schema.name.template");

    /** The name of the schema file in `.moon/cache/schemas/` and in [REMOTE_BASE_URL], for example `project.json`. */
    val fileName: String get() = "$id.json"

    /** The URL of the schema for moon v2. The IDE downloads the schema from this URL. */
    val remoteUrl: String get() = "$REMOTE_BASE_URL$fileName"

    companion object {
        /** The location of the moon v2 schemas on the moon website. */
        const val REMOTE_BASE_URL = "https://moonrepo.dev/schemas/v2/"
    }
}

/** The location of the schema files. */
enum class SchemaSource {
    /** The files that moon wrote to `.moon/cache/schemas/` in the workspace root. */
    LOCAL,

    /** The files at [MoonSchema.REMOTE_BASE_URL]. The plugin uses them only if moon cannot write the local files. */
    REMOTE,
}

/**
 * The data that the JSON schema provider uses to find the schema of a file. [root] is the workspace root with `/` as the separator.
 * [templateDirs] contains the root directory of each template that `moon templates` gave. The set uses the same case rule as [FileUtil.startsWith].
 * [source] tells where the provider gets the schema files.
 * The class prepares the paths one time. Thus [schemaFor] compares strings and does not make a [Path].
 */
data class SchemaMapping(val root: String, val templateDirs: Set<String>, val source: SchemaSource) {

    private val moonDir = child(root, MOON_DIR)
    private val moonCache = child(moonDir, CACHE_DIR)
    private val moonTasks = child(moonDir, TASKS_DIR)

    /** The directory with the local schema files, `.moon/cache/schemas` in [root]. */
    val schemasDir: String = child(moonCache, SCHEMAS_DIR)

    /** Gives the path of the local file of [schema], with `/` as the separator. */
    fun localSchemaPath(schema: MoonSchema): String = "$schemasDir/${schema.fileName}"

    /**
     * Gives the schema of the moon configuration file [path], or null if [path] is not a moon configuration file of this workspace.
     * A file of this workspace is in [root] or in a directory of [templateDirs].
     * [path] is a path of the VFS, with `/` as the separator. The function does not read files.
     *
     * - `moon.<ext>` in the workspace, but not in `.moon/`: [MoonSchema.PROJECT]
     * - `.moon/workspace.<ext>`, `.moon/toolchains.<ext>`, and `.moon/extensions.<ext>`: [MoonSchema.WORKSPACE], [MoonSchema.TOOLCHAINS], and [MoonSchema.EXTENSIONS]
     * - All files in `.moon/tasks/` and in its subdirectories: [MoonSchema.TASKS]
     * - `template.<ext>` in a directory of [templateDirs]: [MoonSchema.TEMPLATE]. This directory can be outside [root].
     *
     * `<ext>` is an extension of [SCHEMA_EXTENSIONS]. The files in `.moon/cache/` of [root] get no schema.
     */
    fun schemaFor(path: String): MoonSchema? {
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        // A name without a base name, for example `.yml`, is not a configuration file.
        if (dot <= slash + 1 || SCHEMA_SUFFIXES.none(path::endsWith)) return null
        // moon writes the files in .moon/cache/. A template from a remote location can also be in this directory.
        if (FileUtil.startsWith(path, moonCache)) return null
        val parent = path.substring(0, slash)
        val baseName = path.substring(slash + 1, dot)
        // A template directory can be outside the workspace root, for example `generator.templates: ['../templates']`.
        if (baseName == TEMPLATE_CONFIG && parent in templateDirs) return MoonSchema.TEMPLATE
        if (!FileUtil.startsWith(path, root)) return null
        if (FileUtil.startsWith(path, moonDir)) {
            return when {
                FileUtil.startsWith(path, moonTasks) -> MoonSchema.TASKS
                !FileUtil.pathsEqual(parent, moonDir) -> null
                baseName == WORKSPACE_CONFIG -> MoonSchema.WORKSPACE
                baseName == TOOLCHAINS_CONFIG -> MoonSchema.TOOLCHAINS
                baseName == EXTENSIONS_CONFIG -> MoonSchema.EXTENSIONS
                else -> null
            }
        }
        return if (baseName == PROJECT_CONFIG) MoonSchema.PROJECT else null
    }

    companion object {
        /** The extensions of the configuration files that the IDE can examine with a JSON schema. The IDE does not do this for TOML, HCL, and Pkl. */
        val SCHEMA_EXTENSIONS = listOf("yml", "yaml", "json", "jsonc")

        private val SCHEMA_SUFFIXES = SCHEMA_EXTENSIONS.map { ".$it" }

        private const val CACHE_DIR = "cache"
        private const val SCHEMAS_DIR = "schemas"
        private const val TASKS_DIR = "tasks"
        private const val TOOLCHAINS_CONFIG = "toolchains"
        private const val EXTENSIONS_CONFIG = "extensions"
        private const val TEMPLATE_CONFIG = "template"

        /** Makes the mapping for the workspace [root] and the template directories [templateDirs]. */
        fun of(root: Path, templateDirs: Collection<Path> = emptyList(), source: SchemaSource = SchemaSource.LOCAL): SchemaMapping =
            SchemaMapping(vfsPath(root.toAbsolutePath().normalize()), templateDirSet(templateDirs.map { vfsPath(it.toAbsolutePath().normalize()) }), source)

        /** Gives a set of [dirs] with the same case rule as [FileUtil.startsWith]. */
        internal fun templateDirSet(dirs: Collection<String>): Set<String> = CollectionFactory.createFilePathSet().apply { addAll(dirs) }

        /** Adds [name] to [dir]. A root directory, for example `/` or `C:/`, already ends with `/`. */
        private fun child(dir: String, name: String): String = if (dir.endsWith('/')) "$dir$name" else "$dir/$name"
    }
}
