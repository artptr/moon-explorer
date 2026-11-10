package io.github.artptr.moonexplorer.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.github.artptr.moonexplorer.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Updates the run icons, the inheritance icons, and the Code Vision hints of the open moon configuration files after a load.
 * These items use only the loaded data. The IDE does not update them when the data changes. Thus the refresher starts the analysis of
 * these files again.
 *
 * The IDE does not calculate the Code Vision hints again if the PSI did not change. A load does not change the PSI.
 * Thus the refresher also clears the modification stamp of each editor of the file.
 *
 * The refresher examines the configuration files of the old data and of the new data. Thus the refresher also removes the icon of a removed task.
 * Only YAML files have these items. Thus the refresher examines only the YAML files. The refresher does not use YAML classes.
 * If a later contributor adds run icons for a different file format, add this format to [runMarkerPaths].
 */
@Service(Service.Level.PROJECT)
class RunMarkersRefresher(private val project: Project, scope: CoroutineScope) {

    /**
     * Starts the analysis of a file again. Tests replace this value with a test function. Change it only on the test thread.
     * The refresher calls it on the EDT.
     */
    internal var restart: (PsiFile) -> Unit = { DaemonCodeAnalyzer.getInstance(project).restart(it, this) }

    init {
        // The IDE makes a service one time. Thus only one coroutine monitors the state.
        val service = project.service<MoonTasksService>()
        scope.launch {
            var previous: TasksState.Loaded? = null
            service.state.map { it.lastLoaded }.distinctUntilChanged().collect { loaded ->
                val paths = runMarkerPaths(previous) + runMarkerPaths(loaded)
                previous = loaded
                if (paths.isNotEmpty()) restartOpenFiles(paths)
            }
        }
    }

    private suspend fun restartOpenFiles(paths: Set<Path>) {
        val files = readAction {
            FileEditorManager.getInstance(project).openFiles
                .filter { it.isValid && it.configFilePath() in paths }
                .mapNotNull { PsiManager.getInstance(project).findFile(it) }
        }
        if (files.isEmpty()) return
        withContext(Dispatchers.EDT) {
            val editorManager = FileEditorManager.getInstance(project)
            files.filter { it.isValid }.forEach { file ->
                editorManager.getEditors(file.virtualFile).filterIsInstance<TextEditor>().forEach {
                    ModificationStampUtil.clearModificationStamp(it.editor)
                }
                restart(file)
            }
        }
    }
}

/**
 * Gives the YAML configuration files of the projects and the inherited YAML files of [state].
 * Only these files have run icons, inheritance icons, and Code Vision hints.
 */
internal fun runMarkerPaths(state: TasksState.Loaded?): Set<Path> =
    state?.configFiles?.let { it.projects.values + it.inherited.values }?.filterTo(HashSet(), ::isYamlFile).orEmpty()
