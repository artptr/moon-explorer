package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Starts a refresh of the tasks after a change of a moon configuration file. [MoonConfigFiles] decides which changes apply.
 * The VFS sends the changes in the IDE and the changes from outside the IDE, for example from `git checkout`.
 * The listener does not read files and does not start moon. [MoonTasksService.scheduleRefresh] waits, then refreshes on a background thread.
 */
class MoonConfigFileListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        if (project.isDisposed) return
        val service = project.service<MoonTasksService>()
        val watch = service.configWatch
        if (events.any { isConfigChange(it, watch) }) service.scheduleRefresh()
    }

    private fun isConfigChange(event: VFileEvent, watch: ConfigWatch): Boolean {
        // moon reads only the local disk. Files in archives or in other file systems do not apply.
        if (event.fileSystem !is LocalFileSystem) return false
        return when (event) {
            is VFileMoveEvent -> changed(event.oldPath, event.file.isDirectory, watch) || changed(event.newPath, false, watch)
            is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME &&
                (changed(event.oldPath, event.file.isDirectory, watch) || changed(event.newPath, false, watch))
            is VFileDeleteEvent -> changed(event.path, event.file.isDirectory, watch)
            is VFileCopyEvent -> changed(event.path, false, watch)
            else -> changed(event.path, false, watch)
        }
    }

    private fun changed(path: String, removedDirectory: Boolean, watch: ConfigWatch): Boolean {
        val file = try {
            Path.of(path)
        } catch (_: InvalidPathException) {
            return false
        }
        return MoonConfigFiles.isConfigChange(file, removedDirectory, watch)
    }
}
