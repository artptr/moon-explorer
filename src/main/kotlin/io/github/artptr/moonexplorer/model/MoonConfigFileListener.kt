package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Starts a refresh of the tasks after a change of a moon configuration file. [MoonConfigFiles] decides which changes apply.
 * The VFS sends the changes in the IDE and the changes from outside the IDE, for example from `git checkout`.
 * The listener does not read files and does not start moon. [MoonTasksService.scheduleRefresh] waits, then refreshes on a background thread.
 */
class MoonConfigFileListener(private val project: Project) : BulkFileListener {

    private val service by lazy { project.service<MoonTasksService>() }

    override fun after(events: List<VFileEvent>) {
        if (project.isDisposed) return
        val watch = service.configWatch
        // Before the first load, the plugin does not know the directories. No change applies.
        if (watch.root == null && watch.projectDir == null) return
        if (events.any { isConfigChange(it, watch) }) service.scheduleRefresh()
    }

    private fun isConfigChange(event: VFileEvent, watch: ConfigWatch): Boolean {
        // moon reads only the local disk. Files in archives or in other file systems do not apply.
        if (event.fileSystem !is LocalFileSystem) return false
        return when (event) {
            is VFileMoveEvent -> changed(event.oldPath, watch) || changed(event.newPath, watch)
            is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME &&
                (changed(event.oldPath, watch) || changed(event.newPath, watch))
            else -> changed(event.path, watch)
        }
    }

    private fun changed(path: String, watch: ConfigWatch): Boolean = MoonConfigFiles.isConfigChange(path, watch)
}
