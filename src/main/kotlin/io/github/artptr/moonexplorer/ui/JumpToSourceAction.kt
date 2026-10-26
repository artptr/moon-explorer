package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.SimpleListCellRenderer
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.findTaskKeyOffset
import io.github.artptr.moonexplorer.model.projectSourceFile
import io.github.artptr.moonexplorer.model.taskSourceFiles
import io.github.artptr.moonexplorer.model.workspacePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the configuration file of the selected task or project. The action has the shortcuts of **Jump to Source**.
 * For a task, the IDE opens the file at the line of the task key. If the task is in more than one file, the IDE shows a list of the files.
 * For a project, the IDE opens the configuration file of the project at the first line.
 * The action does not start moon, and it is not available if the selection has no configuration file.
 * The action uses the configuration files that the plugin found at the last successful load. Thus [update] does not read the disk.
 */
class JumpToSourceAction : DumbAwareAction() {

    /**
     * Shows [files] with the title [title] near the selection in [dataContext]. [label] gives the text that the list shows for a file.
     * The function calls [onChosen] with the file that the user selects. The list keeps the sequence of [files].
     * Tests replace this value with a test function. Change it only on the EDT.
     */
    internal var showChooser: (
        title: String,
        files: List<SourceFile>,
        label: (SourceFile) -> String,
        dataContext: DataContext,
        onChosen: (SourceFile) -> Unit,
    ) -> Unit = ::showFileChooser

    /** The last coroutine that the action started. Tests wait for it. */
    @Volatile
    internal var lastJob: Job? = null
        private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val state = e.getData(SHOWN_TASKS)
        e.presentation.isEnabledAndVisible = e.project != null && state != null &&
            sourceFiles(state, e.getData(SELECTED_TASK), e.getData(SELECTED_PROJECT)).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = e.getData(SHOWN_TASKS) ?: return
        val task = e.getData(SELECTED_TASK)
        val files = sourceFiles(state, task, e.getData(SELECTED_PROJECT))
        when {
            files.size == 1 -> launch(project) { openSourceFile(project, files.single()) }
            files.size > 1 && task != null -> {
                val title = MoonExplorerBundle.message("tasks.jumpToSource.chooseFile", task.target)
                showChooser(title, files, { state.workspacePath(it.path) }, e.dataContext) { file ->
                    launch(project) { openSourceFile(project, file) }
                }
            }
        }
    }

    private fun launch(project: Project, block: suspend () -> Unit) {
        lastJob = project.service<MoonTasksService>().scope.launch { block() }
    }

    /** Gives the files of [task], or the file of [project] if [task] is null. The function does not read the disk. */
    private fun sourceFiles(state: TasksState.Loaded, task: MoonTask?, project: MoonProject?): List<SourceFile> = when {
        task != null -> taskSourceFiles(state, task)
        project != null -> listOfNotNull(projectSourceFile(state, project.id))
        else -> emptyList()
    }
}

/**
 * Opens [file] in the editor. If [SourceFile.taskId] is not null, the editor shows the line of the task key.
 * If the function does not find the task key, the editor shows the first line.
 * The state keeps the paths until the next successful load. If a path is now missing or is a directory, the function does nothing.
 */
internal suspend fun openSourceFile(project: Project, file: SourceFile) {
    val virtualFile = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.path) }
        ?.takeUnless { it.isDirectory } ?: return
    val offset = file.taskId?.let { taskId ->
        // The document contains the changes that the user did not save.
        val text = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.immutableCharSequence }
        text?.let { findTaskKeyOffset(it, taskId) }
    } ?: 0
    withContext(Dispatchers.EDT) {
        OpenFileDescriptor(project, virtualFile, offset).navigate(true)
    }
}

private fun showFileChooser(
    title: String,
    files: List<SourceFile>,
    label: (SourceFile) -> String,
    dataContext: DataContext,
    onChosen: (SourceFile) -> Unit,
) {
    JBPopupFactory.getInstance().createPopupChooserBuilder(files)
        .setTitle(title)
        .setRenderer(
            SimpleListCellRenderer.create<SourceFile> { cell, file, _ ->
                cell.icon = FileTypeManager.getInstance().getFileTypeByFileName(file.path.fileName.toString()).icon
                cell.text = label(file)
            },
        )
        .setItemChosenCallback(onChosen)
        .createPopup()
        .showInBestPositionFor(dataContext)
}
