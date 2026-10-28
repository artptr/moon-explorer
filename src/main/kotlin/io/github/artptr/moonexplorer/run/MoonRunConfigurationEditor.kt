package io.github.artptr.moonexplorer.run

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.github.artptr.moonexplorer.MoonExplorerBundle
import javax.swing.JComponent

/** The editor of a moon run configuration in Run | Edit Configurations. */
class MoonRunConfigurationEditor : SettingsEditor<MoonRunConfiguration>() {

    internal val targetField = JBTextField()

    internal val argumentsField = RawCommandLineEditor()

    internal val environmentField = EnvironmentVariablesTextFieldWithBrowseButton()

    internal val affectedCheckBox = JBCheckBox(MoonExplorerBundle.message("run.configuration.affected.label"))

    internal val forceCheckBox = JBCheckBox(MoonExplorerBundle.message("run.configuration.force.label"))

    override fun resetEditorFrom(configuration: MoonRunConfiguration) {
        targetField.text = configuration.target
        argumentsField.text = configuration.arguments
        environmentField.data = configuration.environmentVariables
        affectedCheckBox.isSelected = configuration.affected
        forceCheckBox.isSelected = configuration.force
    }

    override fun applyEditorTo(configuration: MoonRunConfiguration) {
        configuration.target = targetField.text
        configuration.arguments = argumentsField.text
        configuration.environmentVariables = environmentField.data
        configuration.affected = affectedCheckBox.isSelected
        configuration.force = forceCheckBox.isSelected
    }

    override fun createEditor(): JComponent = panel {
        row(MoonExplorerBundle.message("run.configuration.target.label")) {
            cell(targetField)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("run.configuration.target.comment"))
        }
        row(MoonExplorerBundle.message("run.configuration.arguments.label")) {
            cell(argumentsField)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("run.configuration.arguments.comment"))
        }
        row(MoonExplorerBundle.message("run.configuration.environment.label")) {
            cell(environmentField)
                .align(AlignX.FILL)
                .comment(MoonExplorerBundle.message("run.configuration.environment.comment"))
        }
        row {
            cell(affectedCheckBox)
                .comment(MoonExplorerBundle.message("run.configuration.affected.comment"))
        }
        row {
            cell(forceCheckBox)
                .comment(MoonExplorerBundle.message("run.configuration.force.comment"))
        }
    }
}
