package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.SearchEnvironment
import io.github.artptr.moonexplorer.testData.FAKE_MOON_NAME
import io.github.artptr.moonexplorer.testData.fakeMoonExecutable
import java.io.File
import java.nio.file.Path

class MoonExecutableServiceTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonExecutableService>()

    /** An environment without PATH and PROTO_HOME, and with an empty home directory. Thus a moon on this computer is not found. */
    private val env by lazy {
        SearchEnvironment(emptyMap(), FileUtil.createTempDirectory("home", null, true).toPath(), SystemInfo.isWindows)
    }

    /** Makes a workspace with `.moon/workspace.yml`, `node_modules/.bin/moon`, and the subdirectory `apps/web`. */
    private fun workspace(): Path {
        val root = FileUtil.createTempDirectory("workspace", null, true).toPath()
        root.resolve(".moon").toFile().mkdirs()
        root.resolve(".moon/workspace.yml").toFile().writeText("")
        root.resolve("apps/web").toFile().mkdirs()
        fakeMoonExecutable(root.resolve("node_modules/.bin"))
        return root
    }

    fun testFindUsesTheWorkspaceRootOfTheProjectDirectory() {
        val root = workspace()
        val result = service.find("", root.resolve("apps/web"), env)
        assertEquals(root.toRealPath().resolve("node_modules/.bin/$FAKE_MOON_NAME"), result.executable?.toRealPath())
    }

    fun testFindWithoutWorkspaceDoesNotExamineNodeModules() {
        val result = service.find("", null, env)
        assertNull(result.executable)
        assertTrue(result.examined.none { "node_modules" in it.toString() })
    }

    fun testFindUsesTheGivenSettingsPath() {
        val moon = fakeMoonExecutable()
        assertEquals(moon, service.find(moon.toString(), workspace().resolve("apps/web"), env).executable)
    }

    fun testRelativeSettingsPathStartsAtTheWorkspaceRoot() {
        val root = workspace()
        fakeMoonExecutable(root.resolve("tools"))
        val result = service.find("tools/$FAKE_MOON_NAME", root.resolve("apps/web"), env)
        assertEquals(root.toRealPath().resolve("tools/$FAKE_MOON_NAME"), result.executable?.toRealPath())
    }
}
