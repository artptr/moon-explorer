package io.github.artptr.moonexplorer.model

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.MOON_DIR
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.PROJECT_CONFIG
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.WORKSPACE_CONFIG
import java.nio.file.Path

/**
 * The data that [MoonConfigFiles] uses to find a change of the moon configuration.
 * [root] is the workspace root of the last load, or null if the plugin did not find a workspace. [projectDir] is the IDE project directory.
 * [sources] contains the locations of the projects from the last successful load.
 * The constructor prepares the paths one time, as strings with `/` as the separator, as in the paths of the VFS.
 * Thus the VFS listener compares strings and does not make a [Path] for each event.
 */
class ConfigWatch(root: Path?, projectDir: Path?, val sources: List<ProjectSource> = emptyList()) {
    val root: Path? = root?.toAbsolutePath()?.normalize()
    val projectDir: Path? = projectDir?.toAbsolutePath()?.normalize()

    /** The root with `/` as the separator. */
    internal val rootPrefix: String? = this.root?.let(::vfsPath)

    /** The project directory with `/` as the separator. */
    internal val projectDirPrefix: String? = this.projectDir?.let(::vfsPath)

    /** The directory `.moon` in the root. */
    internal val moonDirPrefix: String? = rootPrefix?.let { child(it, MOON_DIR) }

    /** The directory `.moon/cache` in the root. moon writes to this directory. A change in it does not change the configuration. */
    internal val moonCachePrefix: String? = moonDirPrefix?.let { child(it, CACHE_DIR) }

    /**
     * The project directories of [sources] and all their parent directories in [root]. A source that is not a valid path on this computer
     * is not in the set. The VFS listener does one lookup for each event, not one comparison for each project.
     * Use [isSourceDir] to do the lookup.
     */
    private val sourceDirs: Set<String> = this.root?.let { r ->
        sources.asSequence()
            .mapNotNull { resolveInRoot(r, it.source) }
            .flatMap { dir -> generateSequence(dir) { it.parent }.takeWhile { it.startsWith(r) } }
            .map { sourceDirKey(vfsPath(it)) }
            .toHashSet()
    }.orEmpty()

    /** Gives true if [path] is a project directory of [sources] or one of their parent directories in [root]. */
    internal fun isSourceDir(path: String): Boolean = sourceDirs.isNotEmpty() && sourceDirKey(path) in sourceDirs

    private companion object {
        const val CACHE_DIR = "cache"

        fun vfsPath(path: Path): String = FileUtil.toSystemIndependentName(path.toString())

        /** Adds [name] to [dir]. A root directory, for example `/` or `C:/`, already ends with `/`. */
        fun child(dir: String, name: String): String = if (dir.endsWith('/')) "$dir$name" else "$dir/$name"

        /** Gives a key for [sourceDirs]. On a file system that ignores the case of names, the key also ignores the case. */
        fun sourceDirKey(path: String): String = if (SystemInfoRt.isFileSystemCaseSensitive) path else path.lowercase()
    }
}

/** Finds the changes of the moon configuration files after which the tree shows old tasks. */
object MoonConfigFiles {

    private val PROJECT_FILE_SUFFIXES = configFileSuffixes("/$PROJECT_CONFIG")
    private val WORKSPACE_FILE_SUFFIXES = configFileSuffixes("/$MOON_DIR/$WORKSPACE_CONFIG")

    /** Gives `<base>.<ext>` for each extension that moon accepts. */
    private fun configFileSuffixes(base: String) = MoonWorkspaceLocator.CONFIG_FILE_EXTENSIONS.map { "$base.$it" }

    /**
     * Gives true if a change of [path] can change the output of `moon query projects`. [path] is a path of the VFS, with `/` as the separator.
     * For a move or a rename, call the function for the old path and for the new path.
     * A project directory or one of its parent directories applies. Thus the deletion of a project applies, also without an event for its `moon.yml`.
     * Set [structural] to false for a change of the content of a file. Such a change cannot make or delete a directory.
     * The function does not read files. It compares only strings and makes no new objects for most paths. Thus you can call it in a VFS listener.
     *
     * **NOTE:** If `git checkout` makes a new project directory, the VFS can send an event only for the directory, not for its `moon.yml`.
     * The function does not see the `moon.yml` file. A later change or a manual refresh corrects the tree.
     */
    fun isConfigChange(path: String, watch: ConfigWatch, structural: Boolean = true): Boolean {
        // A new workspace file can make a workspace or a nearer workspace root, for example after `moon init` in a subdirectory.
        if (isWorkspaceFileForProject(path, watch.projectDirPrefix)) return true
        val root = watch.rootPrefix ?: return false
        if (!FileUtil.startsWith(path, root)) return false
        val moonDir = watch.moonDirPrefix
        if (moonDir != null && FileUtil.startsWith(path, moonDir)) {
            val cache = watch.moonCachePrefix
            return cache == null || !FileUtil.startsWith(path, cache)
        }
        if (PROJECT_FILE_SUFFIXES.any { path.endsWith(it) }) return true
        return structural && watch.isSourceDir(path)
    }

    /**
     * Gives true if [path] is `.moon/workspace.<ext>` in [projectDir] or in one of its parent directories.
     * The plugin looks for the workspace root in these directories.
     */
    private fun isWorkspaceFileForProject(path: String, projectDir: String?): Boolean {
        if (projectDir == null) return false
        val suffix = WORKSPACE_FILE_SUFFIXES.firstOrNull { path.endsWith(it) } ?: return false
        // The VFS path of a root directory, for example `/`, can lose its last `/` here. Then the directory is empty or `C:`.
        val dir = path.substring(0, path.length - suffix.length).ifEmpty { "/" }
        return FileUtil.startsWith(projectDir, dir)
    }
}
