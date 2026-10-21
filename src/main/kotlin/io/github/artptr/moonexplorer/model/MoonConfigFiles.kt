package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import java.nio.file.Path

/**
 * The data that [MoonConfigFiles] uses to find a change of the moon configuration.
 * [root] is the workspace root of the last load, or null if the plugin did not find a workspace. [projectDir] is the IDE project directory.
 * [sources] contains the locations of the projects from the last successful load.
 */
data class ConfigWatch(val root: Path?, val projectDir: Path?, val sources: List<ProjectSource> = emptyList())

/** Finds the changes of the moon configuration files after which the tree shows old tasks. */
object MoonConfigFiles {

    private const val MOON_DIR = ".moon"
    private const val CACHE_DIR = "cache"
    private val CONFIG_EXTENSIONS = MoonWorkspaceLocator.WORKSPACE_FILE_EXTENSIONS.toSet()

    /**
     * Gives true if a change of [path] can change the output of `moon query projects`.
     * [removedDirectory] is true if [path] is a directory that the user deleted, moved, or renamed. [path] is then the old path.
     * The function does not read files. Thus you can call it in a VFS listener.
     *
     * **NOTE:** If `git checkout` makes a new project directory, the VFS can send an event only for the directory, not for its `moon.yml`.
     * The function does not see the `moon.yml` file. A later change or a manual refresh corrects the tree.
     */
    fun isConfigChange(path: Path, removedDirectory: Boolean, watch: ConfigWatch): Boolean {
        val file = path.toAbsolutePath().normalize()
        val root = watch.root?.toAbsolutePath()?.normalize() ?: return isNewWorkspaceFile(file, watch.projectDir)
        if (!file.startsWith(root)) return false
        val rel = root.relativize(file)
        if (rel.nameCount > 0 && rel.getName(0).toString() == MOON_DIR) {
            return !(rel.nameCount > 1 && rel.getName(1).toString() == CACHE_DIR)
        }
        if (isProjectConfigName(file.fileName?.toString())) return true
        return removedDirectory && watch.sources.any { source ->
            // A source that is not a valid path on this computer is not in the directory.
            runCatching { root.resolve(source.source).normalize().startsWith(file) }.getOrDefault(false)
        }
    }

    /** Gives true if [name] is `moon.<ext>` with an extension that moon accepts. */
    private fun isProjectConfigName(name: String?): Boolean =
        name != null && name.substringBeforeLast('.') == "moon" && name.substringAfterLast('.', "") in CONFIG_EXTENSIONS

    /**
     * Gives true if [file] is `.moon/workspace.<ext>` in [projectDir] or in one of its parent directories.
     * The plugin looks for the workspace root in these directories. Thus a new workspace file can make a workspace.
     */
    private fun isNewWorkspaceFile(file: Path, projectDir: Path?): Boolean {
        val name = file.fileName?.toString() ?: return false
        if (name.substringBeforeLast('.') != "workspace" || name.substringAfterLast('.', "") !in CONFIG_EXTENSIONS) return false
        val moonDir = file.parent ?: return false
        if (moonDir.fileName?.toString() != MOON_DIR) return false
        val dir = moonDir.parent ?: return false
        val start = projectDir?.toAbsolutePath()?.normalize() ?: return false
        return start.startsWith(dir)
    }
}
