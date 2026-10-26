package io.github.artptr.moonexplorer.model

import com.intellij.openapi.util.io.FileUtil
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.MOON_DIR
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.PROJECT_CONFIG
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator.WORKSPACE_CONFIG
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * The data that [MoonConfigFiles] uses to find a change of the moon configuration.
 * [root] is the workspace root of the last load, or null if the plugin did not find a workspace. [projectDir] is the IDE project directory.
 * [sources] contains the locations of the projects from the last successful load.
 * The constructor prepares the paths one time. Thus the VFS listener does not do this work for each event.
 */
class ConfigWatch(root: Path?, projectDir: Path?, val sources: List<ProjectSource> = emptyList()) {
    val root: Path? = root?.toAbsolutePath()?.normalize()
    val projectDir: Path? = projectDir?.toAbsolutePath()?.normalize()

    /**
     * The project directories of [sources] and all their parent directories in [root]. A source that is not a valid path on this computer
     * is not in the set. The VFS listener does one lookup for each event, not one comparison for each project.
     */
    internal val sourceDirs: Set<Path> = this.root?.let { r ->
        sources.asSequence()
            .mapNotNull { resolveInRoot(r, it.source) }
            .flatMap { dir -> generateSequence(dir) { it.parent }.takeWhile { it.startsWith(r) } }
            .toHashSet()
    }.orEmpty()

    /** The root with `/` as the separator, as in the paths of the VFS. */
    internal val rootPrefix: String? = this.root?.let { FileUtil.toSystemIndependentName(it.toString()) }
}

/** Finds the changes of the moon configuration files after which the tree shows old tasks. */
object MoonConfigFiles {

    private const val CACHE_DIR = "cache"
    private const val WORKSPACE_FILE_PART = "/$MOON_DIR/$WORKSPACE_CONFIG."
    private val CONFIG_EXTENSIONS = MoonWorkspaceLocator.CONFIG_FILE_EXTENSIONS.toSet()

    /**
     * Gives true if a change of [path] can change the output of `moon query projects`. [path] is a path of the VFS, with `/` as the separator.
     * For a move or a rename, call the function for the old path and for the new path.
     * A project directory or one of its parent directories applies. Thus the deletion of a project applies, also without an event for its `moon.yml`.
     * The function does not read files. Thus you can call it in a VFS listener.
     *
     * **NOTE:** If `git checkout` makes a new project directory, the VFS can send an event only for the directory, not for its `moon.yml`.
     * The function does not see the `moon.yml` file. A later change or a manual refresh corrects the tree.
     */
    fun isConfigChange(path: String, watch: ConfigWatch): Boolean {
        // The VFS sends the events of all files of the IDE. A string comparison removes most of them before the plugin makes a Path.
        val prefix = watch.rootPrefix
        if ((prefix == null || !path.startsWith(prefix)) && !path.contains(WORKSPACE_FILE_PART)) return false
        val file = try {
            Path.of(path)
        } catch (_: InvalidPathException) {
            return false
        }
        // A new workspace file can make a workspace or a nearer workspace root, for example after `moon init` in a subdirectory.
        if (isWorkspaceFileForProject(file, watch.projectDir)) return true
        val root = watch.root
        if (root == null || !file.startsWith(root)) return false
        val rel = root.relativize(file)
        if (rel.nameCount > 0 && rel.getName(0).toString() == MOON_DIR) {
            return !(rel.nameCount > 1 && rel.getName(1).toString() == CACHE_DIR)
        }
        return isConfigName(file, PROJECT_CONFIG) || file in watch.sourceDirs
    }

    /** Gives true if the name of [file] is `<base>.<ext>` with an extension that moon accepts. */
    private fun isConfigName(file: Path, base: String): Boolean {
        if (file.fileName == null) return false
        return file.nameWithoutExtension == base && file.extension in CONFIG_EXTENSIONS
    }

    /**
     * Gives true if [file] is `.moon/workspace.<ext>` in [projectDir] or in one of its parent directories.
     * The plugin looks for the workspace root in these directories.
     */
    private fun isWorkspaceFileForProject(file: Path, projectDir: Path?): Boolean {
        if (projectDir == null || !isConfigName(file, WORKSPACE_CONFIG)) return false
        val moonDir = file.parent ?: return false
        if (moonDir.fileName?.toString() != MOON_DIR) return false
        val dir = moonDir.parent ?: return false
        return projectDir.startsWith(dir)
    }
}
