package io.github.artptr.moonexplorer.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.github.artptr.moonexplorer.MoonExplorerBundle
import javax.swing.JComponent

/** The editor of a moon run configuration in Run | Edit Configurations. */
class MoonRunConfigurationEditor : SettingsEditor<MoonRunConfiguration>() {

    internal val targetField = JBTextField()

    override fun resetEditorFrom(configuration: MoonRunConfiguration) {
        targetField.text = configuration.target
    }

    override fun applyEditorTo(configuration: MoonRunConfiguration) {
        configuration.target = targetField.text
    }

    override fun createEditor(): JComponent = panel {
        row(MoonExplorerBundle.message("run.configuration.target.label")) {
            cell(targetField)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("run.configuration.target.comment"))
        }
    }
}
