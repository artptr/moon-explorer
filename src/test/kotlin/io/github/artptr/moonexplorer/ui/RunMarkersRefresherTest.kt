package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.ExistingConfigFiles
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.model.workspacePath
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class RunMarkersRefresherTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    /** The files that the refresher analyzed again. The test does not start the real analysis. */
    private lateinit var restarted: List<PsiFile>

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
        restarted = project.service<RunMarkersRefresher>().recordRestarts(testRootDisposable, callReal = false)
    }

    override fun tearDown() {
        try {
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun open(path: String) {
        FileEditorManager.getInstance(project).openFile(TestData.workspaceVirtualFile(path), false)
    }

    fun testStartupActivityIsRegistered() {
        // findExtension makes only the extension of this class. Some bundled plugins register extensions with an other interface.
        // setUp already makes the service. Thus the test can only make sure that the activity is registered and that it operates without an error.
        val activity = ExtensionPointName<ProjectActivity>("com.intellij.postStartupActivity")
            .findExtension(UiServicesStartupActivity::class.java) ?: error("plugin.xml does not register the startup activity")
        runBlocking { activity.execute(project) }
    }

    fun testOnlyYamlConfigFilesHaveRunMarkers() {
        val root = TestData.workspaceRoot
        val state = TasksState.Loaded(
            WorkspaceTasks(emptyList()),
            root,
            ExistingConfigFiles(
                projects = mapOf("web" to root.resolve("apps/web/moon.yml"), "api" to root.resolve("apps/api/moon.json")),
                inherited = mapOf(".moon/tasks/all.yaml" to root.resolve(".moon/tasks/all.yaml"), ".moon/tasks/node.pkl" to root.resolve(".moon/tasks/node.pkl")),
            ),
        )
        assertEquals(setOf(root.resolve("apps/web/moon.yml"), root.resolve(".moon/tasks/all.yaml")), runMarkerPaths(state))
        assertEquals(emptySet<Path>(), runMarkerPaths(null))
    }

    fun testLoadRestartsTheOpenConfigFilesOnly() {
        open("apps/web/moon.yml")
        open(".moon/tasks/all.yml")
        open(".moon/workspace.yml")
        service.loadRecorded()
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the files", { restarted.size >= 2 }, 10)
        val state = TestData.recordedState()
        val paths = restarted.map { state.workspacePath(it.virtualFile.toNioPath()) }
        assertEquals(listOf(".moon/tasks/all.yml", "apps/web/moon.yml"), paths.distinct().sorted())
    }
}
