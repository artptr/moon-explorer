package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class MoonExecutableServiceTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonExecutableService>()

    private val moonName = if (SystemInfo.isWindows) "moon.cmd" else "moon"

    /** Makes a workspace with `.moon/workspace.yml`, `node_modules/.bin/moon`, and the subdirectory `apps/web`. */
    private fun workspace(): Path {
        val root = FileUtil.createTempDirectory("workspace", null, true).toPath()
        root.resolve(".moon").toFile().mkdirs()
        root.resolve(".moon/workspace.yml").toFile().writeText("")
        root.resolve("apps/web").toFile().mkdirs()
        root.resolve("node_modules/.bin").toFile().mkdirs()
        root.resolve("node_modules/.bin/$moonName").toFile().apply { writeText(""); setExecutable(true) }
        return root
    }

    fun testFindUsesTheWorkspaceRootOfTheProjectDirectory() {
        val root = workspace()
        val result = service.find("", root.resolve("apps/web"))
        assertEquals(root.toRealPath().resolve("node_modules/.bin/$moonName"), result.examined.first().toRealPath())
        assertEquals(result.examined.first(), result.executable)
    }

    fun testFindWithoutWorkspaceDoesNotExamineNodeModules() {
        val result = service.find("", null)
        assertTrue(result.examined.none { "node_modules" in it.toString() })
    }

    fun testFindUsesTheGivenSettingsPath() {
        val moon = FileUtil.createTempFile(moonName, null, true).apply { setExecutable(true) }.toPath()
        assertEquals(moon, service.find(moon.toString(), workspace().resolve("apps/web")).executable)
    }
}
