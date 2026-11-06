package io.github.artptr.moonexplorer.yaml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.config.ConfigSyntax
import io.github.artptr.moonexplorer.config.InheritedTasksEntry
import io.github.artptr.moonexplorer.config.configSyntax
import io.github.artptr.moonexplorer.testData.TestData

class YamlConfigConverterTest : BasePlatformTestCase() {

    private fun syntax(text: String): ConfigSyntax = configSyntax(myFixture.configureByText("moon.yml", text))!!

    fun testGivesTheKeysOfTheWorkspaceFile() {
        val syntax = syntax(TestData.workspaceText("apps/web/moon.yml"))
        assertEquals(listOf("dev", "build", "preview", "lint", "format"), syntax.taskKeys.map { it.id })
        assertEquals("tasks", syntax.tasksKey?.text)
        assertEquals("language", syntax.firstElement?.text)
        syntax.taskKeys.forEach { assertEquals(it.id, it.element.text) }
    }

    fun testGivesTheEntriesOfInheritedTasks() {
        val syntax = syntax(TestData.workspaceText("apps/web/moon.yml"))
        val entries = syntax.inheritedTasks
        assertEquals(2, entries.size)
        assertInstanceOf(entries[0], InheritedTasksEntry.Exclude::class.java)
        assertEquals("format", entries[0].globalId)
        val rename = entries[1] as InheritedTasksEntry.Rename
        assertEquals("typecheck", rename.globalId)
        assertEquals("tsc", rename.localId)
        assertEquals("typecheck", rename.element.text)
    }

    fun testGivesIncludeInBlockAndFlowForm() {
        val syntax = syntax("workspace:\n  inheritedTasks:\n    include:\n      - lint\n      - 'format'\n    exclude: [a, \"b\"]\n")
        assertEquals(listOf("lint", "format", "a", "b"), syntax.inheritedTasks.map { it.globalId })
        assertEquals(listOf(true, true, false, false), syntax.inheritedTasks.map { it is InheritedTasksEntry.Include })
    }

    fun testQuotedKeyHasItsTextWithoutQuotes() {
        val syntax = syntax("tasks:\n  'lint':\n    command: a\n")
        assertEquals("lint", syntax.taskKeys.single().id)
        assertEquals(0, syntax.taskKeys.single().element.firstChild?.textLength ?: 0)
    }

    fun testFileWithACommentStartsAtTheComment() {
        val syntax = syntax("\n# comment\ntasks:\n  a:\n    command: b\n")
        assertEquals("# comment", syntax.firstElement?.text)
    }

    fun testIgnoresTasksThatAreNotAtTheTopLevel() {
        val syntax = syntax("project:\n  tasks:\n    a: b\n")
        assertNull(syntax.tasksKey)
        assertEmpty(syntax.taskKeys)
    }

    fun testEmptyFileHasNoElements() {
        val syntax = syntax("")
        assertNull(syntax.firstElement)
        assertNull(syntax.tasksKey)
        assertEmpty(syntax.taskKeys)
        assertEmpty(syntax.inheritedTasks)
    }

    fun testFileThatIsNotYamlHasNoSyntax() {
        assertNull(configSyntax(myFixture.configureByText("moon.txt", "tasks:")))
    }

    fun testResultIsCachedUntilTheFileChanges() {
        val file = myFixture.configureByText("moon.yml", "tasks:\n  a:\n    command: b\n")
        val first = configSyntax(file)
        assertSame(first, configSyntax(file))
        myFixture.type("# ")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNotSame(first, configSyntax(file))
    }
}
