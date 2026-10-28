package io.github.artptr.moonexplorer.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
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
 * The refresher does not use YAML classes. Thus it also operates for a different file format.
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
                val paths = configPaths(previous) + configPaths(loaded)
                previous = loaded
                if (paths.isNotEmpty()) restartOpenFiles(paths)
            }
        }
    }

    private suspend fun restartOpenFiles(paths: Set<Path>) {
        val files = readAction {
            FileEditorManager.getInstance(project).openFiles
                .filter { it.isValid && it.toNioPathOrNull()?.toAbsolutePath()?.normalize() in paths }
                .mapNotNull { PsiManager.getInstance(project).findFile(it) }
        }
        if (files.isEmpty()) return
        withContext(Dispatchers.EDT) {
            files.filter { it.isValid }.forEach { restart(it) }
        }
    }

    /** Gives the configuration files of the projects and the inherited files of [state]. */
    private fun configPaths(state: TasksState.Loaded?): Set<Path> =
        state?.configFiles?.let { it.projects.values + it.inherited.values }?.toSet().orEmpty()
}

/** Starts [RunMarkersRefresher] when the user opens the IDE project. The service starts to monitor the state when the IDE makes the service. */
class RunMarkersRefresherStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<RunMarkersRefresher>()
    }
}
