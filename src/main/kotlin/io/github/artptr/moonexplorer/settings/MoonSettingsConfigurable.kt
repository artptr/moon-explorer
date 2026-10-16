package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The settings page Settings | Tools | Moon Explorer. */
class MoonSettingsConfigurable(private val project: Project) : BoundSearchableConfigurable(
    MoonExplorerBundle.message("settings.displayName"),
    "",
    ID,
) {

    private val settings = project.service<MoonSettings>()

    /**
     * Shows the path of the executable that the plugin found. The field is read-only.
     * A text field with a fixed minimum width does not make the page wider if the path is long.
     */
    internal lateinit var foundField: JBTextField
        private set

    private var searchJob: Job? = null

    override fun createPanel(): DialogPanel = panel {
        // The labels are above the fields. Thus each field can use the full width of the page.
        row {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.singleFile()
                    .withTitle(MoonExplorerBundle.message("settings.executablePath.chooserTitle")),
                project,
            )
                .label(MoonExplorerBundle.message("settings.executablePath.label"), LabelPosition.TOP)
                .bindText(settings::executablePath)
                .columns(COLUMNS_MEDIUM)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("settings.executablePath.comment"), MAX_LINE_LENGTH_WORD_WRAP)
        }
        row {
            foundField = textField()
                .label(MoonExplorerBundle.message("settings.found.label"), LabelPosition.TOP)
                .columns(COLUMNS_MEDIUM)
                .align(AlignX.FILL)
                .applyToComponent { isEditable = false }
                .component
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
        foundField.text = MoonExplorerBundle.message("settings.found.searching")
        val service = project.service<MoonExecutableService>()
        return service.scope.launch(Dispatchers.IO) {
            val executable = service.find().executable
            // The settings dialog is modal. Thus the update must run in any modality state.
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                foundField.text = executable?.toString() ?: MoonExplorerBundle.message("settings.found.notFound")
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
