package io.github.artptr.moonexplorer.testData

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path

/** The name of a fake `moon` executable. On Windows, the plugin accepts only files with an executable extension. */
val FAKE_MOON_NAME: String = if (SystemInfo.isWindows) "moon.cmd" else "moon"

/**
 * Makes an executable file [name] with the text [content] in [dir]. The function makes [dir] if it does not exist.
 * The default [dir] is a new temporary directory. The test framework deletes this directory after the test.
 */
fun fakeMoonExecutable(
    dir: Path = FileUtil.createTempDirectory("fake-moon", null, true).toPath(),
    name: String = FAKE_MOON_NAME,
    content: String = "",
): Path {
    val file = dir.resolve(name)
    Files.createDirectories(file.parent)
    return file.toFile().apply {
        writeText(content)
        setExecutable(true)
    }.toPath()
}
