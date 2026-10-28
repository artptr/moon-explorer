package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
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

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
        project.service<RunMarkersRefresher>().restart = { file ->
            restarted += TestData.workspaceRoot.relativize(file.virtualFile.toNioPath()).toString().replace('\\', '/')
        }
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
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(TestData.workspaceRoot.resolve(path)) ?: error("No file $path")
        FileEditorManager.getInstance(project).openFile(file, false)
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
