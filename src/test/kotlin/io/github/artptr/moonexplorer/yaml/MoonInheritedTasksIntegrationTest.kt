package io.github.artptr.moonexplorer.yaml

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.config.MoonInheritedTasksGotoDeclarationHandler
import io.github.artptr.moonexplorer.config.MoonInheritedTasksInspection
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Tests the inspection, the navigation, and the completion of the entries of `workspace.inheritedTasks` with the real daemon.
 * The workspace is a copy of [TestData.gutterWorkspace] in a temporary directory. The project `app` can inherit `check` and `clean`
 * from `.moon/tasks/one.yml`, and `clean` also from `.moon/tasks/two.yml`.
 */
class MoonInheritedTasksIntegrationTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    private lateinit var root: Path

    override fun setUp() {
        super.setUp()
        // Use the real path. Thus the paths of the VFS and of the service are the same also if the temporary directory is a symbolic link.
        root = FileUtil.createTempDirectory("moon-inherited", null, true).toPath().toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        FileUtil.copyDir(TestData.gutterWorkspace, root.toFile())
        root.resolve(".moon/tasks/one.yml").writeText("tasks:\n  check:\n    command: 'echo check'\n  clean:\n    command: 'echo clean'\n")
        root.resolve(".moon/tasks/two.yml").writeText("tasks:\n  clean:\n    command: 'echo clean two'\n")
        service.prepareForTests()
        service.projectDir = { root }
        myFixture.enableInspections(MoonInheritedTasksInspection())
    }

    override fun tearDown() {
        try {
            // Close the files before the reset. Thus the IDE does not analyze them again after the deletion of the workspace.
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private val queryJson = """{"projects":[{"id":"app","source":"app","config":{"tasks":{"build":{}}},""" +
        """"inherited":{"layers":{"check":[".moon/tasks/one.yml"],"clean":[".moon/tasks/one.yml",".moon/tasks/two.yml"]}},""" +
        """"tasks":{"build":{"id":"build","target":"app:build"}},"taskTargets":["app:build"]}]}"""

    /** Writes [text] to `app/moon.yaml`, loads the tasks, and opens the file. The text can contain `<caret>`. */
    private fun open(text: String, load: Boolean = true) {
        val caret = text.indexOf("<caret>")
        val file = root.resolve("app/moon.yaml")
        file.writeText(text.replace("<caret>", ""))
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
        if (load) {
            service.queryProjects = { CliResult.Success(queryJson) }
            service.refresh()
            service.awaitState(TestData.loadedState(QueryProjectsParser.parse(queryJson), root))
        }
        myFixture.configureFromExistingVirtualFile(virtualFile)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(caret)
    }

    private fun warnings(): List<HighlightInfo> =
        myFixture.doHighlighting(HighlightSeverity.WARNING).filter { it.inspectionToolId == "MoonInheritedTasks" }

    private fun warningTexts() = warnings().map { it.text }

    private fun targetsAt(offset: Int) =
        MoonInheritedTasksGotoDeclarationHandler().getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, myFixture.editor)

    fun testUnknownTasksHaveWarnings() {
        open("workspace:\n  inheritedTasks:\n    include: [check, chek]\n    exclude:\n      - 'clean'\n      - 'lint'\n    rename:\n      bild: b\n")
        assertEquals(listOf("chek", "'lint'", "bild"), warningTexts())
        assertEquals("No inherited file of this project has the global task 'chek'", warnings().first().description)
    }

    fun testNoWarningsWithoutData() {
        open("workspace:\n  inheritedTasks:\n    include: [chek]\n", load = false)
        assertEmpty(warnings())
    }

    fun testQuickFixRemovesTheEntry() {
        open("workspace:\n  inheritedTasks:\n    include: [check, chek]\n")
        // The file is not in the content of the light project. Thus the fixture does not give the intentions at the caret.
        val fix = myFixture.getAllQuickFixes().single { it.text == "Remove entry" }
        val expected = "workspace:\n  inheritedTasks:\n    include: [check]\n"
        assertEquals(expected, myFixture.getIntentionPreviewText(fix))
        myFixture.launchAction(fix)
        assertEquals(expected, myFixture.editor.document.text)
    }

    fun testGoToDeclarationGivesTheKeyInEachFile() {
        open("workspace:\n  inheritedTasks:\n    exclude: [cl<caret>ean]\n")
        val targets = targetsAt(myFixture.caretOffset)!!
        assertEquals(listOf("one.yml", "two.yml"), targets.map { it.containingFile.name })
        targets.forEach { assertEquals("clean", (it as PsiNamedElement).name) }
    }

    fun testGoToDeclarationIgnoresOtherElements() {
        open("workspace:\n  inheritedTasks:\n    exclude: [chek]\ntasks:\n  bu<caret>ild:\n    command: a\n")
        assertNull(targetsAt(myFixture.caretOffset))
        assertNull(targetsAt(myFixture.editor.document.text.indexOf("chek")))
    }

    fun testCompletionGivesTheGlobalTaskIds() {
        open("workspace:\n  inheritedTasks:\n    include: [<caret>]\n")
        myFixture.completeBasic()
        assertEquals(listOf("check", "clean"), myFixture.lookupElementStrings)
    }

    fun testCompletionSkipsTheIdsOfTheSameList() {
        // The project app can inherit check and clean. The list exclude already has check. The list include does not change the result.
        open("workspace:\n  inheritedTasks:\n    include: [check, clean]\n    exclude: [check, <caret>]\n")
        // The other contributors can also give words of the file. Thus examine only the global task IDs.
        val ids = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertEquals(listOf("clean"), ids.filter { it == "check" || it == "clean" })
    }

    fun testCompletionInAQuotedItemUsesThePrefix() {
        open("workspace:\n  inheritedTasks:\n    exclude:\n      - 'cl<caret>'\n")
        // Other contributors can also give words of the file, for example exclude.
        val ids = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        assertTrue(ids.toString(), "clean" in ids)
        assertFalse(ids.toString(), "check" in ids)
    }

    fun testNoCompletionOutsideTheEntries() {
        open("workspace:\n  inheritedTasks:\n    include: []\ntasks:\n  build:\n    command: <caret>\n")
        myFixture.completeBasic()
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("check"))
    }
}
