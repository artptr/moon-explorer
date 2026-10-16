package io.github.artptr.moonexplorer.cli

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
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

    /** On Windows, only files with these extensions are executables. The search examines them in this sequence. */
    private val WINDOWS_EXECUTABLE_EXTENSIONS = listOf("exe", "cmd", "bat", "com")

    /**
     * Examines these locations in this sequence and gives the first executable:
     * 1. [settingsPath], if it is not empty.
     * 2. `node_modules/.bin/` in [workspaceRoot].
     * 3. The `shims` and `bin` directories of proto: `$PROTO_HOME`, or `~/.proto` if `PROTO_HOME` is not set.
     * 4. The directories in `PATH`.
     *
     * A `~` at the start of [settingsPath] or `PROTO_HOME` is the home directory of the user.
     * A relative [settingsPath] starts at [workspaceRoot].
     * If [settingsPath] is not a valid path, or is not an executable file, the function examines the next locations.
     * Call this function on a background thread.
     */
    fun find(
        settingsPath: String?,
        workspaceRoot: Path?,
        env: SearchEnvironment = SearchEnvironment.current(),
    ): MoonExecutableSearch {
        val names = if (env.isWindows) WINDOWS_EXECUTABLE_EXTENSIONS.map { "moon.$it" } else listOf("moon")
        val examined = mutableListOf<Path>()

        fun firstExecutable(candidates: List<Path>): Path? {
            for (candidate in candidates) {
                examined.add(candidate)
                if (isExecutable(candidate, env.isWindows)) return candidate
            }
            return null
        }

        val settingsText = settingsPath?.trim().orEmpty()
        if (settingsText.isNotEmpty()) {
            val parsed = toPath(settingsText, env.userHome)
            // Do not use a relative path from the working directory of the IDE. That directory has no relation to the workspace.
            val configured = if (parsed == null || parsed.isAbsolute) parsed else workspaceRoot?.resolve(parsed)
            if (configured == null) {
                thisLogger().warn("The path to moon in the settings is not valid or has no workspace root: $settingsText")
            } else {
                firstExecutable(listOf(configured))?.let { return MoonExecutableSearch(it, examined) }
            }
        }

        val workspaceCandidates = workspaceRoot?.let { root -> names.map { root.resolve("node_modules/.bin/$it") } }.orEmpty()
        firstExecutable(workspaceCandidates)?.let { return MoonExecutableSearch(it, examined) }

        val protoHome = protoHome(env)
        val protoCandidates = listOf("shims", "bin").flatMap { dir -> names.map { protoHome.resolve(dir).resolve(it) } }
        firstExecutable(protoCandidates)?.let { return MoonExecutableSearch(it, examined) }

        val separator = if (env.isWindows) ';' else ':'
        val pathDirs = env.environment["PATH"].orEmpty().split(separator)
            .filter { it.isNotBlank() }
            .mapNotNull { toPath(it, env.userHome) }
        for (dir in pathDirs) {
            // The directories in PATH are not in the examined list. The message tells about PATH in one line.
            names.map(dir::resolve).firstOrNull { isExecutable(it, env.isWindows) }
                ?.let { return MoonExecutableSearch(it, examined) }
        }
        return MoonExecutableSearch(null, examined)
    }

    /**
     * Converts [text] to a path. A `~` at the start is [userHome].
     * Gives null if [text] is not a valid path. This function does not examine the file system.
     */
    fun toPath(text: String, userHome: Path): Path? = try {
        when {
            text == "~" -> userHome
            text.startsWith("~/") || text.startsWith("~\\") -> userHome.resolve(text.substring(2))
            else -> Path.of(text)
        }
    } catch (_: InvalidPathException) {
        null
    }

    /** Gives `$PROTO_HOME`, or `~/.proto` if `PROTO_HOME` is not set or is not a valid path. */
    private fun protoHome(env: SearchEnvironment): Path {
        val default = env.userHome.resolve(".proto")
        val text = env.environment["PROTO_HOME"]?.takeIf { it.isNotBlank() } ?: return default
        return toPath(text, env.userHome) ?: default.also {
            thisLogger().warn("PROTO_HOME is not a valid path: $text. The plugin uses $default.")
        }
    }

    private fun isExecutable(file: Path, isWindows: Boolean): Boolean {
        if (!Files.isRegularFile(file)) return false
        // On Windows, the extension tells if a file is an executable. A file without an extension, for example
        // the shell script node_modules/.bin/moon, cannot start.
        return if (isWindows) {
            file.fileName.toString().substringAfterLast('.', "").lowercase() in WINDOWS_EXECUTABLE_EXTENSIONS
        } else {
            Files.isExecutable(file)
        }
    }
}
