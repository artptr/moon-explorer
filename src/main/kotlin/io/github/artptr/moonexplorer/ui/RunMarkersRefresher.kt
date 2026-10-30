package io.github.artptr.moonexplorer.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.configFilePath
import io.github.artptr.moonexplorer.model.isYamlFile
import io.github.artptr.moonexplorer.model.lastLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Updates the run icons in the gutter of the open moon configuration files after a load.
 * The run icons use only the loaded data. The IDE does not update them when the data changes. Thus the refresher starts the analysis of
 * these files again.
 *
 * The refresher examines the configuration files of the old data and of the new data. Thus the refresher also removes the icon of a removed task.
 * Only YAML files have run icons. Thus the refresher examines only the YAML files. The refresher does not use YAML classes.
 * If a later contributor adds run icons for a different file format, add this format to [runMarkerPaths].
 */
@Service(Service.Level.PROJECT)
class RunMarkersRefresher(private val project: Project, scope: CoroutineScope) {

    /**
     * Starts the analysis of [file] again. Tests replace this value with a test function. Change it only on the test thread.
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
            files.filter { it.isValid }.forEach { restart(it) }
        }
    }
}

/** Gives the YAML configuration files of the projects and the inherited YAML files of [state]. Only these files have run icons. */
internal fun runMarkerPaths(state: TasksState.Loaded?): Set<Path> =
    state?.configFiles?.let { it.projects.values + it.inherited.values }?.filterTo(HashSet(), ::isYamlFile).orEmpty()
