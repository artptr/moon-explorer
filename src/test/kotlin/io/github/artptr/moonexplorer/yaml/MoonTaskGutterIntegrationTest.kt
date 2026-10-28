package io.github.artptr.moonexplorer.yaml

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.run.MoonRunConfiguration
import io.github.artptr.moonexplorer.ui.RunMarkersRefresher
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests the run icons in the editor with the real daemon. The workspace is in a temporary directory.
 * It has a project configuration file `moon.yaml` with a quoted key, and a file in `.moon/tasks/` that only one project inherits.
 */
class MoonTaskGutterIntegrationTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    private lateinit var root: Path

    override fun setUp() {
        super.setUp()
        // The tests can read the build directory. On macOS, the VFS gives the system temporary directory as /private/var, which the tests cannot read.
        val tmp = Files.createDirectories(Path.of("build", "tmp", "gutterTest").toAbsolutePath().normalize())
        root = Files.createTempDirectory(tmp, "workspace")
        write(".moon/workspace.yml", "projects:\n  app: 'app'\n")
        write(".moon/tasks/one.yml", "tasks:\n  check:\n    command: 'echo check'\n")
        write("app/moon.yaml", "tasks:\n  'build':\n    command: 'echo build'\n  lint:\n    command: 'echo lint'\n")
        service.prepareForTests()
        service.projectDir = { root }
        project.service<RunMarkersRefresher>()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
            root.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun write(path: String, text: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    /** Gives the output of `moon query projects` for the project `app` with the tasks [own] in `moon.yaml` and the inherited task `check`. */
    private fun queryJson(own: List<String>): String {
        val tasks = (own + "check").joinToString(",") { """"$it":{"id":"$it","target":"app:$it"}""" }
        val config = own.joinToString(",") { """"$it":{}""" }
        return """{"projects":[{"id":"app","source":"app","config":{"tasks":{$config}},""" +
            """"inherited":{"layers":{"check":[".moon/tasks/one.yml"]}},"tasks":{$tasks}}]}"""
    }

    /** Loads the tasks with the output of [queryJson] and waits for the [TasksState.Loaded] state. */
    private fun load(own: List<String>) {
        val json = queryJson(own)
        service.queryProjects = { CliResult.Success(json) }
        val before = service.state.value
        service.refresh()
        PlatformTestUtil.waitWithEventsDispatching("The load did not complete", {
            val state = service.state.value
            state is TasksState.Loaded && state != before
        }, 10)
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
        val daemon = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
        assertTrue(daemon.isAllAnalysisFinished(psiFile))

        // A new task in the loaded data. The refresher must mark the file for a new analysis without a change of the file.
        load(listOf("build", "lint"))
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the analysis", { !daemon.isAllAnalysisFinished(psiFile) }, 10)
        assertEquals(listOf("Run 'app:build'", "Run 'app:lint'"), tooltips())

        // The task goes from the loaded data. The icon must go too.
        load(listOf("build"))
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the analysis", { !daemon.isAllAnalysisFinished(psiFile) }, 10)
        assertEquals(listOf("Run 'app:build'"), tooltips())
    }
}
