package io.github.artptr.moonexplorer.model

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class MoonConfigFileListenerTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private lateinit var dir: Path
    private val calls = AtomicInteger()
    private val delay = 200.milliseconds

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
        // Use the real path. Thus the paths of the VFS and of the service are the same also if the temp directory is a symbolic link.
        dir = FileUtil.createTempDirectory("moon-workspace", null, true).toPath().toRealPath()
        listOf(".moon/workspace.yml", ".moon/tasks/all.yml", ".moon/cache/states/x.json", "apps/web/moon.yml", "README.md", "other/keep.txt").forEach {
            Files.createDirectories(dir.resolve(it).parent)
            Files.writeString(dir.resolve(it), "a: 1\n")
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)!!.also { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
        service.projectDir = { dir }
        val recorded = TestData.recorded("query-projects.json")
        service.queryProjects = { calls.incrementAndGet(); CliResult.Success(recorded) }
        service.refreshDelay = delay
        service.refresh()
        service.awaitState(TasksState.Loaded(TestData.recordedTasks(), dir))
        calls.set(0)
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun file(path: String): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir.resolve(path))!!

    /** Changes [path] in the IDE through the VFS. */
    private fun changeInIde(path: String) {
        val file = file(path)
        runWriteAction { file.setBinaryContent("b: ${System.nanoTime()}\n".toByteArray()) }
    }

    /** Changes [path] on the disk, as `git checkout` does. Then refreshes the VFS. */
    private fun changeOnDisk(path: String) {
        val file = file(path)
        Files.writeString(dir.resolve(path), "c: ${System.nanoTime()}\n")
        VfsUtil.markDirtyAndRefresh(false, false, false, file)
    }

    /** Waits until the service ran [expected] queries. If this does not occur in [millis], the test fails. */
    private fun awaitCalls(expected: Int, millis: Long = 3_000) {
        val end = System.currentTimeMillis() + millis
        while (calls.get() < expected && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(expected, calls.get())
    }

    fun testChangeOfAProjectConfigFileRefreshesInLessThanThreeSeconds() {
        changeInIde("apps/web/moon.yml")
        awaitCalls(1)
    }

    fun testChangeOfATasksFileRefreshes() {
        changeInIde(".moon/tasks/all.yml")
        awaitCalls(1)
    }

    fun testChangeFromOutsideTheIdeRefreshes() {
        changeOnDisk(".moon/tasks/all.yml")
        awaitCalls(1)
    }

    fun testChangeInTheCacheDoesNotRefresh() {
        changeInIde(".moon/cache/states/x.json")
        changeOnDisk(".moon/cache/states/x.json")
        Thread.sleep(delay.inWholeMilliseconds * 3)
        assertEquals(0, calls.get())
    }

    fun testChangeOfOtherFilesDoesNotRefresh() {
        changeInIde("README.md")
        Thread.sleep(delay.inWholeMilliseconds * 3)
        assertEquals(0, calls.get())
    }

    fun testRenameOfAProjectConfigFileRefreshes() {
        val file = file("apps/web/moon.yml")
        runWriteAction { file.rename(this, "moon.yml.bak") }
        awaitCalls(1)
    }

    fun testMoveOfATasksFileRefreshes() {
        val file = file(".moon/tasks/all.yml")
        val target = file("other")
        runWriteAction { file.move(this, target) }
        awaitCalls(1)
    }

    fun testDeleteOfAProjectDirectoryRefreshes() {
        // The recorded output has the project web in apps/web. The VFS sends one event for the directory, not for its moon.yml.
        val dir = file("apps/web")
        runWriteAction { dir.delete(this) }
        awaitCalls(1)
    }

    fun testRenameOfAParentOfAProjectDirectoryRefreshes() {
        val dir = file("apps")
        runWriteAction { dir.rename(this, "applications") }
        awaitCalls(1)
    }

    fun testMoveOfOtherFilesDoesNotRefresh() {
        val file = file("README.md")
        val target = file("other")
        runWriteAction { file.move(this, target) }
        Thread.sleep(delay.inWholeMilliseconds * 3)
        assertEquals(0, calls.get())
    }

    fun testManyChangesCauseOneRefresh() {
        repeat(10) { changeInIde("apps/web/moon.yml") }
        awaitCalls(1)
        Thread.sleep(delay.inWholeMilliseconds * 3)
        assertEquals(1, calls.get())
    }
}
