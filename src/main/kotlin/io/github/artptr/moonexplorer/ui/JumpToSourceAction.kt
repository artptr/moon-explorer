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
import com.intellij.ui.ColoredListCellRenderer
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.findTaskKeyOffset
import io.github.artptr.moonexplorer.model.isYamlFile
import io.github.artptr.moonexplorer.model.projectSourceFile
import io.github.artptr.moonexplorer.model.taskSourceFiles
import io.github.artptr.moonexplorer.model.workspacePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JList

/**
 * Opens the configuration file of the selected task or project. The action has the shortcuts of **Jump to Source**.
 * For a task, the IDE opens the file at the line of the task key. If the task is in more than one file, the IDE shows a list of the files.
 * For a project, the IDE opens the configuration file of the project at the first line.
 * The action does not start moon, and it is not available if the selection has no configuration file.
 */
class JumpToSourceAction : DumbAwareAction() {

    /**
     * Shows the files of [choices] with the title [title] near the selection in [dataContext]. The function calls [onChosen] with the file that
     * the user selects. Each item of [choices] is a file and the text that the list shows for it. The list keeps the sequence of [choices].
     * Tests replace this value with a test function. Change it only on the EDT.
     */
    internal var showChooser: (
        title: String,
        choices: List<Pair<SourceFile, String>>,
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
        val selectedProject = e.getData(SELECTED_PROJECT)
        if (task == null && selectedProject == null) return
        // The data context is a snapshot. Thus the popup can use it after the action completes.
        val dataContext = e.dataContext
        launch(project) {
            val files = withContext(Dispatchers.IO) { sourceFiles(state, task, selectedProject) }
            when {
                files.size == 1 -> openSourceFile(project, files.single())
                files.size > 1 && task != null -> withContext(Dispatchers.EDT) {
                    val title = MoonExplorerBundle.message("tasks.jumpToSource.chooseFile", task.target)
                    showChooser(title, files.map { it to state.workspacePath(it.path) }, dataContext) { file ->
                        launch(project) { openSourceFile(project, file) }
                    }
                }
            }
        }
    }

    private fun launch(project: Project, block: suspend () -> Unit) {
        lastJob = project.service<MoonTasksService>().scope.launch { block() }
    }

    /** Gives the files of [task], or the file of [project] if [task] is null. The function reads the disk. Call it on a background thread. */
    private fun sourceFiles(state: TasksState.Loaded, task: MoonTask?, project: MoonProject?): List<SourceFile> = when {
        task != null -> taskSourceFiles(state, task)
        project != null -> listOfNotNull(projectSourceFile(state, project.id))
        else -> emptyList()
    }
}

/**
 * Opens [file] in the editor. If [SourceFile.taskId] is not null and the file is a YAML file, the editor shows the line of the task key.
 * If the function does not find the task key, the editor shows the first line.
 */
internal suspend fun openSourceFile(project: Project, file: SourceFile) {
    val virtualFile = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.path) } ?: return
    val taskId = file.taskId?.takeIf { isYamlFile(file.path) }
    val offset = taskId?.let {
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
    choices: List<Pair<SourceFile, String>>,
    dataContext: DataContext,
    onChosen: (SourceFile) -> Unit,
) {
    val labels = choices.toMap()
    JBPopupFactory.getInstance().createPopupChooserBuilder(choices.map { it.first })
        .setTitle(title)
        .setRenderer(
            object : ColoredListCellRenderer<SourceFile>() {
                override fun customizeCellRenderer(
                    list: JList<out SourceFile>,
                    value: SourceFile,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    icon = FileTypeManager.getInstance().getFileTypeByFileName(value.path.fileName.toString()).icon
                    append(labels.getValue(value))
                }
            },
        )
        .setItemChosenCallback(onChosen)
        .createPopup()
        .showInBestPositionFor(dataContext)
}
