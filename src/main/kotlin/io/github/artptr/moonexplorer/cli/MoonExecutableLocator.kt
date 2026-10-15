package io.github.artptr.moonexplorer.cli

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The result of a search for the `moon` executable.
 * [executable] is null if the plugin cannot find moon.
 * [examined] contains the files that the plugin examined before the directories in `PATH`.
 */
data class MoonExecutableSearch(val executable: Path?, val examined: List<Path>)

/** The environment of the IDE that the search uses. Tests use temporary directories. */
data class SearchEnvironment(
    val environment: Map<String, String>,
    val userHome: Path,
    val isWindows: Boolean,
) {
    companion object {
        /** The environment of the IDE. On macOS, this environment also contains the values from the login shell. */
        fun current() = SearchEnvironment(
            EnvironmentUtil.getEnvironmentMap(),
            Path.of(System.getProperty("user.home")),
            SystemInfo.isWindows,
        )
    }
}

/** Finds the `moon` executable. */
object MoonExecutableLocator {

    /**
     * Examines these locations in this sequence and gives the first executable:
     * 1. [settingsPath], if it is not blank.
     * 2. `node_modules/.bin/` in [workspaceRoot].
     * 3. The `shims` and `bin` directories of proto: `$PROTO_HOME`, or `~/.proto` if `PROTO_HOME` is not set.
     * 4. The directories in `PATH`.
     *
     * Call this function on a background thread.
     */
    fun find(
        settingsPath: String?,
        workspaceRoot: Path?,
        env: SearchEnvironment = SearchEnvironment.current(),
    ): MoonExecutableSearch {
        val names = if (env.isWindows) listOf("moon.exe", "moon.cmd") else listOf("moon")
        val examined = mutableListOf<Path>()

        fun firstExecutable(candidates: List<Path>): Path? {
            for (candidate in candidates) {
                examined.add(candidate)
                if (isExecutable(candidate, env.isWindows)) return candidate
            }
            return null
        }

        val configured = settingsPath?.trim().orEmpty().takeIf { it.isNotEmpty() }?.let(::toPath)
        configured?.let { firstExecutable(listOf(it)) }?.let { return MoonExecutableSearch(it, examined) }

        val workspaceCandidates = workspaceRoot?.let { root -> names.map { root.resolve("node_modules/.bin/$it") } }.orEmpty()
        firstExecutable(workspaceCandidates)?.let { return MoonExecutableSearch(it, examined) }

        val protoHome = env.environment["PROTO_HOME"]?.takeIf { it.isNotBlank() }?.let(::toPath)
            ?: env.userHome.resolve(".proto")
        val protoCandidates = listOf("shims", "bin").flatMap { dir -> names.map { protoHome.resolve(dir).resolve(it) } }
        firstExecutable(protoCandidates)?.let { return MoonExecutableSearch(it, examined) }

        val pathDirs = env.environment["PATH"].orEmpty().split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .mapNotNull(::toPath)
        for (dir in pathDirs) {
            // The directories in PATH are not in the examined list. The message tells about PATH in one line.
            names.map(dir::resolve).firstOrNull { isExecutable(it, env.isWindows) }
                ?.let { return MoonExecutableSearch(it, examined) }
        }
        return MoonExecutableSearch(null, examined)
    }

    private fun toPath(text: String): Path? = try {
        Path.of(text)
    } catch (_: InvalidPathException) {
        null
    }

    private fun isExecutable(file: Path, isWindows: Boolean): Boolean =
        Files.isRegularFile(file) && (isWindows || Files.isExecutable(file))
}
