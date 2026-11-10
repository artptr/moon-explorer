package io.github.artptr.moonexplorer.yaml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.config.ConfigSyntax
import io.github.artptr.moonexplorer.config.InheritedTasksEntry
import io.github.artptr.moonexplorer.config.configSyntax
import io.github.artptr.moonexplorer.config.inheritedTasksEntryRemoval
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
        // The hints and the icons attach to the leaf of the key. The text of this leaf contains the quotes.
        assertEquals("'lint'", syntax.taskKeys.single().element.text)
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

    /** Removes the entry with the global ID [globalId] from [text] and gives the new text. */
    private fun removeEntry(text: String, globalId: String): String {
        val entry = syntax(text).inheritedTasks.first { it.globalId == globalId }
        return inheritedTasksEntryRemoval(entry.element)!!.applyTo(text)
    }

    private fun settings(body: String) = "workspace:\n  inheritedTasks:\n$body"

    fun testRemovesAnItemOfABlockList() {
        val text = settings("    include:\n      - lint\n      - lnt\n      - test\n")
        assertEquals(settings("    include:\n      - lint\n      - test\n"), removeEntry(text, "lnt"))
        assertEquals(settings("    include:\n      - lnt\n      - test\n"), removeEntry(text, "lint"))
        assertEquals(settings("    include:\n      - lint\n      - lnt\n"), removeEntry(text, "test"))
    }

    fun testRemovesAnItemOfAFlowList() {
        val text = settings("    exclude: ['a', b, c]\n")
        assertEquals(settings("    exclude: [b, c]\n"), removeEntry(text, "a"))
        assertEquals(settings("    exclude: ['a', c]\n"), removeEntry(text, "b"))
        assertEquals(settings("    exclude: ['a', b]\n"), removeEntry(text, "c"))
    }

    fun testLastItemGivesAnEmptyList() {
        // An empty include list removes all global tasks. Without the list, the project inherits all global tasks.
        assertEquals(settings("    include: []\n"), removeEntry(settings("    include:\n      - lnt\n"), "lnt"))
        assertEquals(settings("    include: []\n"), removeEntry(settings("    include: [lnt]\n"), "lnt"))
    }

    fun testElementThatIsNotAnEntryHasNoRemoval() {
        val syntax = syntax(settings("    include: [lint]\ntasks:\n  lint: {}\n"))
        assertNull(inheritedTasksEntryRemoval(syntax.taskKeys.single().element))
    }

    fun testRemovesAPairOfRename() {
        assertEquals(settings("    rename:\n      typecheck: tsc\n"), removeEntry(settings("    rename:\n      lnt: l\n      typecheck: tsc\n"), "lnt"))
        assertEquals(settings("    rename: {typecheck: tsc}\n"), removeEntry(settings("    rename: {lnt: l, typecheck: tsc}\n"), "lnt"))
        assertEquals(settings("    rename: {}\n"), removeEntry(settings("    rename:\n      lnt: l\n"), "lnt"))
    }
}
