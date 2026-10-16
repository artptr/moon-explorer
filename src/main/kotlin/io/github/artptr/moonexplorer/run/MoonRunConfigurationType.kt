package io.github.artptr.moonexplorer.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle

/** The run configuration type "moon". A configuration runs `moon run <target>`. */
class MoonRunConfigurationType : ConfigurationTypeBase(
    ID,
    MoonExplorerBundle.message("run.configuration.type.name"),
    MoonExplorerBundle.message("run.configuration.type.description"),
    AllIcons.Actions.Execute,
) {
    init {
        addFactory(MoonConfigurationFactory(this))
    }

    /** The factory of the type. The type has only this factory. */
    val factory: ConfigurationFactory get() = configurationFactories.single()

    companion object {
        const val ID = "MoonRunConfiguration"

        fun getInstance(): MoonRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(MoonRunConfigurationType::class.java)
    }
}

/** Makes moon run configurations. */
class MoonConfigurationFactory(type: MoonRunConfigurationType) : ConfigurationFactory(type) {

    // The ID is in the saved configurations. Do not change it.
    override fun getId(): String = "moon"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        MoonRunConfiguration(project, this, MoonExplorerBundle.message("run.configuration.type.name"))

    override fun getOptionsClass(): Class<out BaseState> = MoonRunConfigurationOptions::class.java
}
