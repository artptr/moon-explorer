package io.github.artptr.moonexplorer.cli

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/** Starts a fake `moon` script. The tests do not start moon. The script is a shell script, thus the tests do not run on Windows. */
class MoonCliProcessTest : BasePlatformTestCase() {

    /** Makes a script that writes its arguments and the value of NO_COLOR, and stops with [exitCode]. */
    private fun fakeMoon(exitCode: Int = 0): File {
        val dir = FileUtil.createTempDirectory("fake-moon", null, true)
        return File(dir, "moon").apply {
            writeText("#!/bin/sh\necho \"$@ NO_COLOR=${'$'}NO_COLOR\"\nexit $exitCode\n")
            setExecutable(true)
        }
    }

    private fun queryTasks(settingsPath: String): CliResult {
        val workspace = FileUtil.createTempDirectory("workspace", null, true).toPath()
        return runBlocking { withTimeout(10_000) { withContext(Dispatchers.IO) { MoonCli.queryTasks(workspace, settingsPath) } } }
    }

    fun testQueryTasksRunsTheExecutableFromTheSettings() {
        if (SystemInfo.isWindows) return
        assertEquals(CliResult.Success("query tasks NO_COLOR=1\n"), queryTasks(fakeMoon().path))
    }

    fun testQueryTasksGivesTheErrorOfTheExecutable() {
        if (SystemInfo.isWindows) return
        assertEquals(CliResult.Failed("query tasks NO_COLOR=1"), queryTasks(fakeMoon(exitCode = 2).path))
    }
}
