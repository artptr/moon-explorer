package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.ThreadingAssertions
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.MoonExecutableLocator
import io.github.artptr.moonexplorer.model.MoonTasksService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.event.DocumentEvent

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
                .validationOnInput { field ->
                    // Do only a check of the text here. This function runs on the EDT, thus it must not read files.
                    val text = field.text.trim()
                    if (text.isNotEmpty() && MoonExecutableLocator.toPath(text, Path.of(SystemProperties.getUserHome())) == null) {
                        error(MoonExplorerBundle.message("settings.executablePath.invalid"))
                    } else {
                        null
                    }
                }
                .applyToComponent {
                    // Find moon again when the user changes the path. Thus the page shows the result before Apply.
                    textField.document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            updateFoundPath(text)
                        }
                    })
                }
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
        updateFoundPath(settings.executablePath)
    }

    override fun apply() {
        val changed = isModified
        super.apply()
        updateFoundPath(settings.executablePath)
        // Load the tasks again with the new executable.
        if (changed) project.service<MoonTasksService>().refresh()
    }

    /**
     * Finds moon on a background thread with [settingsPath], and shows the result. Call this function on the EDT.
     * The function waits [delayMillis] before the search. A new call cancels the previous search.
     * Thus the page does not examine the files after each key that the user pushes.
     */
    internal fun updateFoundPath(settingsPath: String, delayMillis: Long = SEARCH_DELAY_MILLIS): Job {
        // The check of searchJob in the search below is correct only if all calls run on the EDT.
        ThreadingAssertions.assertEventDispatchThread()
        searchJob?.cancel()
        foundField.text = MoonExplorerBundle.message("settings.found.searching")
        val service = project.service<MoonExecutableService>()
        return service.scope.launch(Dispatchers.IO) {
            delay(delayMillis)
            val executable = service.find(settingsPath).executable
            val search = coroutineContext.job
            // The settings dialog is modal. Thus the update must run in any modality state.
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                // A newer search or the close of the page cancels this search. Then the result is not current.
                // This block and updateFoundPath run on the EDT. Thus searchJob has its value when this block runs.
                if (!isActive || searchJob !== search) return@withContext
                foundField.text = executable?.toString() ?: MoonExplorerBundle.message("settings.found.notFound")
            }
        }.also { searchJob = it }
    }

    override fun disposeUIResources() {
        searchJob?.cancel()
        searchJob = null
        super.disposeUIResources()
    }

    companion object {
        const val ID = "io.github.artptr.moonexplorer.settings"

        private const val SEARCH_DELAY_MILLIS = 300L
    }
}
