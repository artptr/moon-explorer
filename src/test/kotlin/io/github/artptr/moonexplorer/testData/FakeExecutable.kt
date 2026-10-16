package io.github.artptr.moonexplorer.testData

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path

/** The name of a fake `moon` executable. On Windows, the plugin accepts only files with an executable extension. */
val FAKE_MOON_NAME: String = if (SystemInfo.isWindows) "moon.cmd" else "moon"

/**
 * Makes an empty executable file with the name [FAKE_MOON_NAME] in a new temporary directory.
 * The test framework deletes the directory after the test.
 */
fun fakeMoonExecutable(): Path {
    val dir = FileUtil.createTempDirectory("fake-moon", null, true)
    return dir.resolve(FAKE_MOON_NAME).apply {
        writeText("")
        setExecutable(true)
    }.toPath()
}
