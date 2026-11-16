package io.github.artptr.moonexplorer.json

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import io.github.artptr.moonexplorer.schema.MoonSchemaListener

/**
 * Tells the JSON schema service of the IDE to find the schemas again when the moon schema data changes.
 * The JSON schema service keeps the schema of each file in a cache. The resetter removes this cache. Then the IDE examines the open files again.
 * `moon-explorer-json.xml` registers this listener.
 */
class MoonSchemaResetter(private val project: Project) : MoonSchemaListener {
    override fun schemasChanged() {
        if (project.isDisposed) return
        // The reset also restarts the daemon. Thus the IDE examines the open files again.
        project.service<JsonSchemaService>().reset()
    }
}
