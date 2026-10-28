package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.util.Collections

class RunMarkersRefresherTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    /** The paths of the files that the refresher analyzed again. Each path starts at the workspace root. */
    private val restarted: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val refresher get() = project.service<RunMarkersRefresher>()

    private lateinit var defaultRestart: (PsiFile) -> Unit

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
        defaultRestart = refresher.restart
        refresher.restart = { file ->
            restarted += TestData.workspaceRoot.relativize(file.virtualFile.toNioPath()).toString().replace('\\', '/')
        }
    }

    override fun tearDown() {
        try {
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
            // The light project is the same for all tests. Other tests use the real restart.
            refresher.restart = defaultRestart
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun open(path: String) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(TestData.workspaceRoot.resolve(path)) ?: error("No file $path")
        FileEditorManager.getInstance(project).openFile(file, false)
    }

    fun testStartupActivityIsRegisteredAndStartsTheRefresher() {
        // findExtension makes only the extension of this class. Some bundled plugins register extensions with an other interface.
        val activity = ExtensionPointName<ProjectActivity>("com.intellij.postStartupActivity")
            .findExtension(RunMarkersRefresherStartupActivity::class.java) ?: error("plugin.xml does not register the startup activity")
        runBlocking { activity.execute(project) }
        assertNotNull(project.getServiceIfCreated(RunMarkersRefresher::class.java))
    }

    fun testLoadRestartsTheOpenConfigFilesOnly() {
        open("apps/web/moon.yml")
        open(".moon/tasks/all.yml")
        open(".moon/workspace.yml")
        service.loadRecorded()
        PlatformTestUtil.waitWithEventsDispatching("The refresher did not restart the files", { restarted.size >= 2 }, 10)
        assertEquals(listOf(".moon/tasks/all.yml", "apps/web/moon.yml"), restarted.distinct().sorted())
    }
}
