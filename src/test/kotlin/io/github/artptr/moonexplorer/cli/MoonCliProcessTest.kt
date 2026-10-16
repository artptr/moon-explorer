package io.github.artptr.moonexplorer.cli

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Starts a fake `moon` script. The tests do not start moon. The script is a shell script, thus the tests do not run on Windows. */
class MoonCliProcessTest {

    // MoonCli uses the progress manager of the platform. The tests need only the application, not a project.
    @get:Rule
    val application = ApplicationRule()

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun notOnWindows() {
        assumeFalse("The fake moon is a shell script", SystemInfo.isWindows)
    }

    /** Makes a script that writes its arguments and the value of NO_COLOR, and stops with [exitCode]. */
    private fun fakeMoon(exitCode: Int = 0): File = File(temp.newFolder("fake-moon"), "moon").apply {
        writeText("#!/bin/sh\necho \"$@ NO_COLOR=\$NO_COLOR\"\nexit $exitCode\n")
        setExecutable(true)
    }

    private fun queryTasks(executable: File): CliResult {
        val workspace = temp.newFolder("workspace").toPath()
        return runBlocking { withTimeout(10_000) { withContext(Dispatchers.IO) { MoonCli.query(executable.toPath(), workspace, "tasks") } } }
    }

    @Test
    fun queryRunsTheExecutable() {
        assertEquals(CliResult.Success("query tasks NO_COLOR=1\n"), queryTasks(fakeMoon()))
    }

    @Test
    fun queryGivesTheErrorOfTheExecutable() {
        assertEquals(CliResult.Failed("query tasks NO_COLOR=1"), queryTasks(fakeMoon(exitCode = 2)))
    }
}
