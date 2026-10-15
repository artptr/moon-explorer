package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.cli.MoonExecutableLocator
import io.github.artptr.moonexplorer.cli.MoonExecutableSearch
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/** Finds the `moon` executable for the IDE project. All moon commands of the plugin use this executable. */
@Service(Service.Level.PROJECT)
class MoonExecutableService(private val project: Project, private val cs: CoroutineScope) {

    /** The coroutine scope of the service. The settings page uses it to find moon on a background thread. */
    val scope: CoroutineScope get() = cs

    /** The path to moon from the plugin settings. An empty value tells the plugin to find moon automatically. */
    val settingsPath: String get() = project.service<MoonSettings>().executablePath

    /**
     * Finds moon with the path from the settings and the workspace root of the IDE project.
     * Call this function on a background thread.
     */
    fun find(): MoonExecutableSearch {
        val root = project.basePath?.let { MoonWorkspaceLocator.findWorkspaceRoot(Path.of(it)) }
        return MoonExecutableLocator.find(settingsPath, root)
    }
}
