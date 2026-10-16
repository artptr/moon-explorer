package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.cli.MoonExecutableLocator
import io.github.artptr.moonexplorer.cli.MoonExecutableSearch
import io.github.artptr.moonexplorer.cli.MoonWorkspaceLocator
import io.github.artptr.moonexplorer.cli.SearchEnvironment
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Finds the `moon` executable for the IDE project. All moon commands of the plugin use this executable.
 * [scope] is the coroutine scope of the service. The settings page uses it to find moon on a background thread.
 */
@Service(Service.Level.PROJECT)
class MoonExecutableService(private val project: Project, val scope: CoroutineScope) {

    /** The path to moon from the plugin settings. An empty value tells the plugin to find moon automatically. */
    private val settingsPath: String get() = project.service<MoonSettings>().executablePath

    /**
     * Finds moon with [settingsPath] and the workspace root of the IDE project.
     * The default value of [settingsPath] is the path from the settings. The settings page gives the text of its field.
     * Call this function on a background thread.
     */
    fun find(settingsPath: String = this.settingsPath): MoonExecutableSearch =
        find(settingsPath, project.basePath?.let(Path::of))

    /**
     * Finds moon with the path from the settings in [workspaceRoot]. Use this function if the caller has the workspace root.
     * Call this function on a background thread.
     */
    fun findInWorkspace(workspaceRoot: Path?): MoonExecutableSearch = MoonExecutableLocator.find(settingsPath, workspaceRoot)

    /** Gives the workspace root of the IDE project, or null if the IDE project does not contain a moon workspace. */
    fun workspaceRoot(): Path? = project.basePath?.let { MoonWorkspaceLocator.findWorkspaceRoot(Path.of(it)) }

    /** Finds the workspace root from [projectDir] and then finds moon in [env]. Tests give the values. */
    internal fun find(
        settingsPath: String,
        projectDir: Path?,
        env: SearchEnvironment = SearchEnvironment.current(),
    ): MoonExecutableSearch {
        val root = projectDir?.let(MoonWorkspaceLocator::findWorkspaceRoot)
        return MoonExecutableLocator.find(settingsPath, root, env)
    }
}
