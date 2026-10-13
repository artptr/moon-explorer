package io.github.artptr.moonexplorer.model

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Loads the tasks when the user opens the IDE project. */
class MoonTasksStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<MoonTasksService>().refresh()
    }
}
