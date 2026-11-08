package io.github.artptr.moonexplorer.yaml

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import io.github.artptr.moonexplorer.config.MoonCodeVisionProvider
import io.github.artptr.moonexplorer.config.MoonInheritedCodeVisionProvider
import io.github.artptr.moonexplorer.config.MoonMergedCodeVisionProvider
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.run.MoonRunConfiguration
import io.github.artptr.moonexplorer.testData.TestData
import io.github.artptr.moonexplorer.ui.RunMarkersRefresher
import io.github.artptr.moonexplorer.ui.recordRestarts
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Tests the run icons in the editor with the real daemon. The workspace is a copy of [TestData.gutterWorkspace] in a temporary directory.
 * The output of `moon query projects` comes from [queryJson]. Thus each test can change the tasks.
 */
class MoonTaskGutterIntegrationTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    private lateinit var root: Path

    /** The files that the refresher analyzed again. The refresher also does the real restart. */
    private lateinit var restarted: MutableList<PsiFile>

    override fun setUp() {
        super.setUp()
        // Use the real path. Thus the paths of the VFS and of the service are the same also if the temp directory is a symbolic link.
        root = FileUtil.createTempDirectory("moon-gutter", null, true).toPath().toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        FileUtil.copyDir(TestData.gutterWorkspace, root.toFile())
        service.prepareForTests()
        service.projectDir = { root }
        restarted = project.service<RunMarkersRefresher>().recordRestarts(testRootDisposable, callReal = true)
    }

    override fun tearDown() {
        try {
            // Close the files before the reset. Thus the refresher does not analyze them again after the deletion of the workspace.
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    /**
     * Gives the output of `moon query projects` for the project `app` with the tasks [own] in `moon.yaml`.
     * The project inherits each task of [global] from `.moon/tasks/one.yml`. A task in [own] and in [global] merges.
     */
    private fun queryJson(own: List<String>, global: List<String>): String {
        val all = (own + global).distinct()
        val tasks = all.joinToString(",") { """"$it":{"id":"$it","target":"app:$it"}""" }
        val targets = all.joinToString(",") { """"app:$it"""" }
        val config = own.joinToString(",") { """"$it":{}""" }
        val layers = global.joinToString(",") { """"$it":[".moon/tasks/one.yml"]""" }
        return """{"projects":[{"id":"app","source":"app","config":{"tasks":{$config}},""" +
            """"inherited":{"layers":{$layers}},"tasks":{$tasks},"taskTargets":[$targets]}]}"""
    }

    /** Loads the tasks with the output of [queryJson] and waits for the [TasksState.Loaded] state of this output. */
    private fun load(own: List<String>, global: List<String> = listOf("check")) {
        val json = queryJson(own, global)
        service.queryProjects = { CliResult.Success(json) }
        service.refresh()
        service.awaitState(TestData.loadedState(QueryProjectsParser.parse(json), root))
    }

    fun testHintStaysOnTheKeyWhileTheUserTypes() {
        load(listOf("check"))
        open(".moon/tasks/one.yml")
        val provider = MoonMergedCodeVisionProvider()
        fun hintText() = provider.computeForEditor(myFixture.editor, myFixture.file).single().first.let { myFixture.editor.document.getText(it) }
        val before = provider.computeForEditor(myFixture.editor, myFixture.file).single().first.startOffset
        assertEquals("check", hintText())
        myFixture.editor.caretModel.moveToOffset(0)
        myFixture.type("# A new line.\n")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("check", hintText())
        assertEquals(before + "# A new line.\n".length, provider.computeForEditor(myFixture.editor, myFixture.file).single().first.startOffset)
    }

    fun testInheritanceIconIsInTheGutter() {
        load(listOf("check"))
        open(".moon/tasks/one.yml")
        assertTrue(myFixture.findAllGutters().any { it.tooltipText == "Merged in 1 project" })
    }

    /** Gives the hints of [provider] in the open file. Each item has the text of the hint and the text of the element below the hint. */
    private fun hints(provider: MoonCodeVisionProvider): List<String> = provider.computeForEditor(myFixture.editor, myFixture.file).map { (range, entry) ->
        (entry as ClickableTextCodeVisionEntry).longPresentation + " @ " + myFixture.editor.document.getText(range)
    }

    fun testHintsOfTheProjectFileStayOnTheKeysWhileTheUserTypes() {
        // The local task lint merges with the global task lint.
        load(listOf("build", "lint"), listOf("check", "lint"))
        open("app/moon.yaml")
        fun all() = hints(MoonInheritedCodeVisionProvider()) + hints(MoonMergedCodeVisionProvider())
        val expected = listOf("2 inherited tasks @ tasks", "Merged with one.yml @ lint")
        assertEquals(expected, all())
        myFixture.editor.caretModel.moveToOffset(0)
        myFixture.type("# A new line.\n")
        // Type after the indent of lint. The editor adds the indent after the line end. Thus lint stays a task key.
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("lint:"))
        myFixture.type("# A line above lint.\n")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(expected, all())
    }

    fun testLocalTaskThatMergesHasTheInheritanceIconInTheGutter() {
        load(listOf("build", "lint"), listOf("check", "lint"))
        open("app/moon.yaml")
        assertTrue(myFixture.findAllGutters().any { it.tooltipText == "Merged with one.yml" })
    }

    private fun open(path: String) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve(path)) ?: error("No file $path")
        myFixture.configureFromExistingVirtualFile(file)
    }

    private fun tooltips(gutters: List<GutterMark> = myFixture.findAllGutters()): List<String?> =
        gutters.map { it.tooltipText }.filter { it?.startsWith("Run ") == true || it?.startsWith("Inherited ") == true }

    fun testProjectFileWithTheYamlExtensionAndAQuotedKeyHasRunIcons() {
        load(listOf("build", "lint"))
        open("app/moon.yaml")
        assertEquals(listOf("Run 'app:build'", "Run 'app:lint'"), tooltips())
    }

    fun testSharedTaskOfOneProjectShowsOneProject() {
        load(listOf("build", "lint"))
        open(".moon/tasks/one.yml")
        assertEquals(listOf("Inherited by 1 project"), tooltips())
    }

    fun testRunFromTheContextMenuOfAQuotedKeyUsesTheTargetOfTheKey() {
        load(listOf("build", "lint"))
        open("app/moon.yaml")
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("uild'"))
        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)
        val context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.EDITOR_POPUP)
        val producer = RunConfigurationProducer.getInstance(MoonTaskRunConfigurationProducer::class.java)
        val configuration = producer.createConfigurationFromContext(context)?.configuration as? MoonRunConfiguration
            ?: error("No configuration from the editor context")
        assertEquals(listOf("app:build"), configuration.targets)
    }

    fun testLoadAnalyzesTheOpenFileAgainAndChangesTheIcons() {
        load(listOf("build"))
        open("app/moon.yaml")
        assertEquals(listOf("Run 'app:build'"), tooltips())
        val psiFile = myFixture.file

        // A new task in the loaded data. The refresher must start the analysis of the open file again without a change of the file.
        // The test waits for the call of the real restart. A wait for the state of the daemon can miss a short analysis.
        restarted.clear()
        load(listOf("build", "lint"))
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the analysis", { psiFile in restarted }, 10)
        assertEquals(listOf("Run 'app:build'", "Run 'app:lint'"), tooltips())

        // The task goes from the loaded data. The icon must go too.
        restarted.clear()
        load(listOf("build"))
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the analysis", { psiFile in restarted }, 10)
        assertEquals(listOf("Run 'app:build'"), tooltips())
    }
}
