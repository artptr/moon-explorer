package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Starts the services of the UI layer when the user opens the IDE project: [RefreshFailureNotifier] and [RunMarkersRefresher].
 * Each service starts to monitor the state when the IDE makes it.
 * The model layer does not use the UI layer. Thus `MoonTasksStartupActivity` does not start these services.
 */
class UiServicesStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<RefreshFailureNotifier>()
        project.service<RunMarkersRefresher>()
    }
}
