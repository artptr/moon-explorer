package io.github.artptr.moonexplorer.cli

import java.nio.file.Files
import java.nio.file.Path

/** Finds the root directory of a moon workspace. */
object MoonWorkspaceLocator {

    /** The file extensions that moon accepts for `.moon/workspace.*`. */
    private val WORKSPACE_FILE_EXTENSIONS = listOf("yml", "yaml", "json", "jsonc", "pkl", "hcl", "toml")

    /**
     * Goes up from [start] through its parent directories and gives the first workspace root.
     * A workspace root has a `.moon/` directory with a `workspace.*` file.
     * The function does not accept a `.moon/` directory without this file. Thus the function does not use `~/.moon`, the moon home directory.
     * Call this function on a background thread.
     */
    fun findWorkspaceRoot(start: Path): Path? =
        generateSequence(start.toAbsolutePath().normalize()) { it.parent }.firstOrNull(::isWorkspaceRoot)

    private fun isWorkspaceRoot(dir: Path): Boolean {
        val moonDir = dir.resolve(".moon")
        return Files.isDirectory(moonDir) &&
            WORKSPACE_FILE_EXTENSIONS.any { Files.isRegularFile(moonDir.resolve("workspace.$it")) }
    }
}
