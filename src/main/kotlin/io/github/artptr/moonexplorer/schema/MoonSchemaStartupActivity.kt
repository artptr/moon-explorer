package io.github.artptr.moonexplorer.schema

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Starts [MoonSchemaService] when the user opens the IDE project. `moon-explorer-json.xml` registers it. Thus it starts only when the JSON plugin is active. */
class MoonSchemaStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<MoonSchemaService>().start()
    }
}
