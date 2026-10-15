package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JLabel

/** The settings page Settings | Tools | Moon Explorer. */
class MoonSettingsConfigurable(private val project: Project) : BoundSearchableConfigurable(
    MoonExplorerBundle.message("settings.displayName"),
    "",
    ID,
) {

    private val settings = project.service<MoonSettings>()

    /** Shows the path of the executable that the plugin found. */
    internal lateinit var foundLabel: JLabel
        private set

    private var searchJob: Job? = null

    override fun createPanel(): DialogPanel = panel {
        row(MoonExplorerBundle.message("settings.executablePath.label")) {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.singleFile()
                    .withTitle(MoonExplorerBundle.message("settings.executablePath.chooserTitle")),
                project,
            )
                .bindText(settings::executablePath)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("settings.executablePath.comment"))
        }
        row(MoonExplorerBundle.message("settings.found.label")) {
            foundLabel = label("").component
        }
    }

    override fun reset() {
        super.reset()
        updateFoundPath()
    }

    override fun apply() {
        val changed = isModified
        super.apply()
        updateFoundPath()
        // Load the tasks again with the new executable.
        if (changed) project.service<MoonTasksService>().refresh()
    }

    /** Finds moon on a background thread with the saved settings, and shows the result. */
    internal fun updateFoundPath(): Job {
        searchJob?.cancel()
        foundLabel.text = MoonExplorerBundle.message("settings.found.searching")
        val service = project.service<MoonExecutableService>()
        return service.scope.launch(Dispatchers.IO) {
            val executable = service.find().executable
            // The settings dialog is modal. Thus the update must run in any modality state.
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                foundLabel.text = executable?.toString() ?: MoonExplorerBundle.message("settings.found.notFound")
            }
        }.also { searchJob = it }
    }

    override fun disposeUIResources() {
        searchJob?.cancel()
        super.disposeUIResources()
    }

    companion object {
        const val ID = "io.github.artptr.moonexplorer.settings"
    }
}
