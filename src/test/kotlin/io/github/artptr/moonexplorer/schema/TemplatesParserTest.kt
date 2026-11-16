package io.github.artptr.moonexplorer.schema

import io.github.artptr.moonexplorer.model.MoonOutputException
import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Path

class TemplatesParserTest {

    @Test
    fun recordedOutput() {
        // The record script replaces the path of the test workspace with /workspace. On Windows, this path is not absolute.
        assertEquals(listOf(TestData.workspacePath("templates/package")), TemplatesParser.parse(recordedTemplates(TestData.workspaceRoot)))
    }

    @Test
    fun entriesWithoutValidRootAreIgnored() {
        val root = Path.of("/ws/templates/a").toAbsolutePath()
        val json = """{"a":{"root":${jsonString(root.toString())}},"b":{"root":"relative/dir"},"c":{"root":1},"d":{},"e":"text"}"""
        assertEquals(listOf(root), TemplatesParser.parse(json))
    }

    @Test
    fun noTemplates() {
        assertEquals(emptyList<Path>(), TemplatesParser.parse("{}"))
        assertEquals(emptyList<Path>(), TemplatesParser.parse("[]"))
    }

    @Test
    fun invalidJson() {
        assertThrows(MoonOutputException::class.java) { TemplatesParser.parse("{") }
    }
}
