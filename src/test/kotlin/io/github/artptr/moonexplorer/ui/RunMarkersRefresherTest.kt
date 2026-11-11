package io.github.artptr.moonexplorer.ui

import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import io.github.artptr.moonexplorer.model.*
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class RunMarkersRefresherTest : MoonTasksServiceTestCase() {

    /** The files that the refresher analyzed again. The test does not start the real analysis. */
    private lateinit var restarted: MutableList<PsiFile>

    override fun setUp() {
        super.setUp()
        restarted = project.service<RunMarkersRefresher>().recordRestarts(testRootDisposable, callReal = false)
    }

    override fun tearDown() {
        try {
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
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

    fun testRefresherExaminesOnlyTheYamlConfigFiles() {
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

    fun testLoadClearsTheCodeVisionStampOfTheOpenConfigFiles() {
        val virtualFile = TestData.workspaceVirtualFile(".moon/tasks/all.yml")
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile), false)!!
        ModificationStampUtil.putCurrentModificationStamp(editor, PsiManager.getInstance(project).findFile(virtualFile)!!)
        assertNotNull(ModificationStampUtil.getModificationStamp(editor))
        service.loadRecorded()
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the file", { restarted.isNotEmpty() }, 10)
        assertNull(ModificationStampUtil.getModificationStamp(editor))
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

    fun testRefresherContinuesAfterAnException() {
        // An exception in the refresher stopped the collection of the state. Then no later load updated the open files.
        val refresher = project.service<RunMarkersRefresher>()
        val record = refresher.restart
        val calls = AtomicInteger()
        refresher.restart = { if (calls.getAndIncrement() == 0) throw IllegalStateException("The first restart fails") else record(it) }
        open(".moon/tasks/all.yml")
        // loadRecorded waits until the refresher handled the loaded data. Thus the first restart occurred.
        service.loadRecorded()
        assertTrue(calls.get() > 0)
        restarted.clear()
        // The removal of the tasks is a change of the loaded data. Thus the refresher examines the files of the old data.
        service.loadNoWorkspace()
        assertNotEmpty(restarted)
    }
}
