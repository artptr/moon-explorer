package io.github.artptr.moonexplorer.cli

import java.nio.file.Files
import java.nio.file.Path

/** Finds the root directory of a moon workspace. */
object MoonWorkspaceLocator {

    /** The file extensions that moon accepts for its configuration files, for example `.moon/workspace.*` and `moon.*`. */
    internal val CONFIG_FILE_EXTENSIONS = listOf("yml", "yaml", "json", "jsonc", "pkl", "hcl", "toml")

    /** The directory of the workspace configuration in the workspace root. */
    internal const val MOON_DIR = ".moon"

    /** The base name of the workspace configuration file in [MOON_DIR], for example `workspace.yml`. */
    internal const val WORKSPACE_CONFIG = "workspace"

    /** The base name of the configuration file of a project, for example `moon.yml`. */
    internal const val PROJECT_CONFIG = "moon"

    /**
     * Goes up from [start] through its parent directories and gives the first workspace root.
     * A workspace root has a `.moon/` directory with a `workspace.*` file.
     * The function does not accept a `.moon/` directory without this file. Thus the function does not use `~/.moon`, the moon home directory.
     * Call this function on a background thread.
     */
    fun findWorkspaceRoot(start: Path): Path? =
        generateSequence(start.toAbsolutePath().normalize()) { it.parent }.firstOrNull(::isWorkspaceRoot)

    /**
     * Gives the file `<baseName>.<ext>` in [dir] with the first extension of [CONFIG_FILE_EXTENSIONS] that exists, for example `moon.yml`.
     * Gives null if no file exists. Call this function on a background thread.
     */
    internal fun findConfigFile(dir: Path, baseName: String): Path? =
        CONFIG_FILE_EXTENSIONS.asSequence().map { dir.resolve("$baseName.$it") }.firstOrNull(Files::isRegularFile)

    private fun isWorkspaceRoot(dir: Path): Boolean {
        val moonDir = dir.resolve(MOON_DIR)
        return Files.isDirectory(moonDir) && findConfigFile(moonDir, WORKSPACE_CONFIG) != null
    }
}
