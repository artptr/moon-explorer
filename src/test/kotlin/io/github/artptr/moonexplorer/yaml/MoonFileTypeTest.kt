package io.github.artptr.moonexplorer.yaml

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.schema.YamlJsonEnabler

/** Tests the rules of ADR 0005 for the file type of `moon.yml` and `moon.yaml`. */
class MoonFileTypeTest : BasePlatformTestCase() {

    private fun fileTypeOf(fileName: String) = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

    fun testMoonFileNamesHaveTheMoonFileType() {
        assertSame(MoonFileType, fileTypeOf("moon.yml"))
        assertSame(MoonFileType, fileTypeOf("moon.yaml"))
    }

    /** The IDE keeps the name in the settings. If the new file type is primary, the YAML language has two primary file types. */
    fun testNameAndSecondaryFlag() {
        assertEquals("Moon", MoonFileType.name)
        assertTrue(MoonFileType.isSecondary)
    }

    fun testYamlLanguageKeepsItsPrimaryFileType() {
        assertSame(YAMLFileType.YML, YAMLLanguage.INSTANCE.associatedFileType)
    }

    fun testOtherYamlFilesKeepTheYamlFileType() {
        for (fileName in listOf("workspace.yml", "node.yml", "other.yaml", "moon.yml.bak.yml")) {
            assertSame(fileName, YAMLFileType.YML, fileTypeOf(fileName))
        }
    }

    fun testMoonFileTypeUsesTheYamlLanguage() {
        assertSame(YAMLLanguage.INSTANCE, MoonFileType.language)
    }

    /** `YamlJsonEnabler` is the condition for the JSON schemas from SchemaStore. */
    fun testJsonSchemasApplyToMoonFile() {
        val file = myFixture.configureByText("moon.yml", "tasks: {}\n")
        assertTrue(YamlJsonEnabler().isEnabledForFile(file.virtualFile, project))
    }
}
