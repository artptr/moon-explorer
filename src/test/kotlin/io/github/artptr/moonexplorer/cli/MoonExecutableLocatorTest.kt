package io.github.artptr.moonexplorer.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class MoonExecutableLocatorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val isWindows = File.separatorChar == '\\'

    private val home by lazy { temp.newFolder("home").toPath() }
    private val workspace by lazy { temp.newFolder("workspace").toPath() }
    private val pathDir by lazy { temp.newFolder("path-bin").toPath() }

    private fun env(vararg values: Pair<String, String>, windows: Boolean = isWindows) =
        SearchEnvironment(mapOf(*values), home, windows)

    /** Makes an executable file `moon` (or `moon.cmd` for Windows) in [dir]. */
    private fun moonIn(dir: Path, name: String = if (isWindows) "moon.cmd" else "moon"): Path {
        val file = dir.resolve(name).toFile()
        file.parentFile.mkdirs()
        file.writeText("")
        file.setExecutable(true)
        return file.toPath()
    }

    @Test
    fun findsNodeModulesWhenMoonIsNotInPath() {
        val moon = moonIn(workspace.resolve("node_modules/.bin"))
        val result = MoonExecutableLocator.find(null, workspace, env("PATH" to pathDir.toString()))
        assertEquals(moon, result.executable)
    }

    @Test
    fun settingsPathIsBeforeAllOtherLocations() {
        val configured = moonIn(temp.newFolder("custom").toPath())
        moonIn(workspace.resolve("node_modules/.bin"))
        moonIn(home.resolve(".proto/shims"))
        moonIn(pathDir)
        val result = MoonExecutableLocator.find(configured.toString(), workspace, env("PATH" to pathDir.toString()))
        assertEquals(configured, result.executable)
        assertEquals(listOf(configured), result.examined)
    }

    @Test
    fun blankSettingsPathFindsMoonAutomatically() {
        val moon = moonIn(workspace.resolve("node_modules/.bin"))
        assertEquals(moon, MoonExecutableLocator.find("  ", workspace, env()).executable)
    }

    @Test
    fun settingsPathThatIsNotAFileGoesToTheNextLocation() {
        val moon = moonIn(workspace.resolve("node_modules/.bin"))
        val missing = temp.root.toPath().resolve("missing/moon")
        val result = MoonExecutableLocator.find(missing.toString(), workspace, env())
        assertEquals(moon, result.executable)
        assertEquals(missing, result.examined.first())
    }

    @Test
    fun nodeModulesIsBeforeProto() {
        val moon = moonIn(workspace.resolve("node_modules/.bin"))
        moonIn(home.resolve(".proto/shims"))
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env()).executable)
    }

    @Test
    fun protoShimsIsBeforeProtoBin() {
        val shim = moonIn(home.resolve(".proto/shims"))
        moonIn(home.resolve(".proto/bin"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env()).executable)
    }

    @Test
    fun findsProtoBin() {
        val bin = moonIn(home.resolve(".proto/bin"))
        assertEquals(bin, MoonExecutableLocator.find(null, workspace, env()).executable)
    }

    @Test
    fun protoHomeVariableReplacesTheDefaultDirectory() {
        val protoHome = temp.newFolder("proto-home").toPath()
        moonIn(home.resolve(".proto/shims"))
        val moon = moonIn(protoHome.resolve("shims"))
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env("PROTO_HOME" to protoHome.toString())).executable)
    }

    @Test
    fun protoIsBeforePath() {
        val shim = moonIn(home.resolve(".proto/shims"))
        moonIn(pathDir)
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env("PATH" to pathDir.toString())).executable)
    }

    @Test
    fun findsMoonInPath() {
        val other = temp.newFolder("other").toPath()
        val moon = moonIn(pathDir)
        val path = listOf(other, pathDir).joinToString(File.pathSeparator)
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env("PATH" to path)).executable)
    }

    @Test
    fun notFoundGivesTheExaminedLocationsInSequence() {
        val result = MoonExecutableLocator.find("/custom/moon", workspace, env(windows = false))
        assertNull(result.executable)
        val expected = listOf(
            Path.of("/custom/moon"),
            workspace.resolve("node_modules/.bin/moon"),
            home.resolve(".proto/shims/moon"),
            home.resolve(".proto/bin/moon"),
        )
        assertEquals(expected, result.examined)
    }

    @Test
    fun withoutWorkspaceSkipsNodeModules() {
        val result = MoonExecutableLocator.find(null, null, env(windows = false))
        assertEquals(listOf(home.resolve(".proto/shims/moon"), home.resolve(".proto/bin/moon")), result.examined)
    }

    @Test
    fun windowsLooksForExeAndCmd() {
        val result = MoonExecutableLocator.find(null, workspace, env(windows = true))
        assertEquals(
            listOf(workspace.resolve("node_modules/.bin/moon.exe"), workspace.resolve("node_modules/.bin/moon.cmd")),
            result.examined.take(2),
        )
    }

    @Test
    fun windowsFindsMoonCmdInNodeModules() {
        val cmd = moonIn(workspace.resolve("node_modules/.bin"), "moon.cmd")
        assertEquals(cmd, MoonExecutableLocator.find(null, workspace, env(windows = true)).executable)
    }

    @Test
    fun fileThatIsNotExecutableIsSkipped() {
        assumeFalse("Windows has no executable permission", isWindows)
        val file = moonIn(workspace.resolve("node_modules/.bin"))
        file.toFile().setExecutable(false)
        val shim = moonIn(home.resolve(".proto/shims"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env()).executable)
    }

    @Test
    fun directoryIsSkipped() {
        workspace.resolve("node_modules/.bin/moon").toFile().mkdirs()
        val shim = moonIn(home.resolve(".proto/shims"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env(windows = false)).executable)
    }

    @Test
    fun tildeInSettingsPathIsTheHomeDirectory() {
        val moon = moonIn(home.resolve("tools"))
        val name = moon.fileName.toString()
        assertEquals(moon, MoonExecutableLocator.find("~/tools/$name", workspace, env()).executable)
    }

    @Test
    fun tildeInProtoHomeIsTheHomeDirectory() {
        val moon = moonIn(home.resolve("custom-proto/shims"))
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env("PROTO_HOME" to "~/custom-proto")).executable)
    }

    @Test
    fun settingsPathThatIsNotValidGoesToTheNextLocation() {
        val moon = moonIn(workspace.resolve("node_modules/.bin"))
        val result = MoonExecutableLocator.find("bad\u0000path", workspace, env())
        assertEquals(moon, result.executable)
        assertEquals(workspace.resolve("node_modules/.bin").resolve(moon.fileName), result.examined.first())
    }

    @Test
    fun protoHomeThatIsNotValidUsesTheDefaultDirectory() {
        val shim = moonIn(home.resolve(".proto/shims"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env("PROTO_HOME" to "bad\u0000path")).executable)
    }

    @Test
    fun toPathDoesNotAcceptAnInvalidPath() {
        assertNull(MoonExecutableLocator.toPath("bad\u0000path", home))
        assertEquals(home, MoonExecutableLocator.toPath("~", home))
        assertEquals(home.resolve("bin/moon"), MoonExecutableLocator.toPath("~/bin/moon", home))
    }

    @Test
    fun windowsPathUsesSemicolons() {
        val other = temp.newFolder("other").toPath()
        val cmd = moonIn(pathDir, "moon.cmd")
        val path = "$other;$pathDir"
        assertEquals(cmd, MoonExecutableLocator.find(null, workspace, env("PATH" to path, windows = true)).executable)
    }

    @Test
    fun windowsSettingsPathWithoutExtensionIsSkipped() {
        // Windows cannot start a file without an extension, for example the shell script node_modules/.bin/moon.
        val script = moonIn(temp.newFolder("scripts").toPath(), "moon")
        val cmd = moonIn(workspace.resolve("node_modules/.bin"), "moon.cmd")
        val result = MoonExecutableLocator.find(script.toString(), workspace, env(windows = true))
        assertEquals(cmd, result.executable)
        assertEquals(script, result.examined.first())
    }

    @Test
    fun windowsSettingsPathWithExeExtensionIsUsed() {
        val exe = moonIn(temp.newFolder("bin").toPath(), "moon.exe")
        assertEquals(exe, MoonExecutableLocator.find(exe.toString(), workspace, env(windows = true)).executable)
    }

    @Test
    fun relativeSettingsPathStartsAtTheWorkspaceRoot() {
        val moon = moonIn(workspace.resolve("tools"))
        val relative = "tools/${moon.fileName}"
        val result = MoonExecutableLocator.find(relative, workspace, env())
        assertEquals(moon, result.executable)
        assertEquals(moon, result.examined.first())
    }

    @Test
    fun relativeSettingsPathWithoutWorkspaceIsSkipped() {
        val shim = moonIn(home.resolve(".proto/shims"))
        val result = MoonExecutableLocator.find("tools/moon", null, env(windows = false))
        assertEquals(shim, result.executable)
        assertEquals(home.resolve(".proto/shims/moon"), result.examined.first())
    }

    @Test
    fun windowsFindsMoonBatInNodeModules() {
        val bat = moonIn(workspace.resolve("node_modules/.bin"), "moon.bat")
        assertEquals(bat, MoonExecutableLocator.find(null, workspace, env(windows = true)).executable)
    }

    @Test
    fun windowsPrefersExeWhenSeveralNamesExist() {
        val bin = workspace.resolve("node_modules/.bin")
        moonIn(bin, "moon.cmd")
        moonIn(bin, "moon.bat")
        val exe = moonIn(bin, "moon.exe")
        assertEquals(exe, MoonExecutableLocator.find(null, workspace, env(windows = true)).executable)
    }

    @Test
    fun blankProtoHomeUsesTheDefaultDirectory() {
        val shim = moonIn(home.resolve(".proto/shims"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env("PROTO_HOME" to "  ")).executable)
    }

    @Test
    fun relativeProtoHomeUsesTheDefaultDirectory() {
        val shim = moonIn(home.resolve(".proto/shims"))
        assertEquals(shim, MoonExecutableLocator.find(null, workspace, env("PROTO_HOME" to "relative/proto")).executable)
    }

    @Test
    fun pathEntryWithTildeStartsAtTheHomeDirectory() {
        val moon = moonIn(home.resolve("bin"))
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env("PATH" to "~/bin")).executable)
    }

    @Test
    fun pathIgnoresBlankInvalidAndRelativeEntriesAndTrimsSpaces() {
        val moon = moonIn(pathDir)
        val path = listOf(" ", "bad\u0000path", "relative-bin", " $pathDir ").joinToString(File.pathSeparator)
        assertEquals(moon, MoonExecutableLocator.find(null, workspace, env("PATH" to path)).executable)
    }

    @Test
    fun pathWithOnlyARelativeEntryFindsNothing() {
        assertNull(MoonExecutableLocator.find(null, null, env("PATH" to "relative-bin", windows = false)).executable)
    }

    @Test
    fun windowsPathVariableNameIsNotCaseSensitive() {
        val cmd = moonIn(pathDir, "moon.cmd")
        assertEquals(cmd, MoonExecutableLocator.find(null, workspace, env("Path" to pathDir.toString(), windows = true)).executable)
    }
}
