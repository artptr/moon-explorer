package io.github.artptr.moonexplorer.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.MoonIcons

/** The run configuration type "moon". A configuration runs `moon run <target>`. */
class MoonRunConfigurationType : ConfigurationTypeBase(
    ID,
    MoonExplorerBundle.message("run.configuration.type.name"),
    MoonExplorerBundle.message("run.configuration.type.description"),
    MoonIcons.Moon,
) {
    /** The factory of the type. The type has only this factory. */
    val factory: ConfigurationFactory = MoonConfigurationFactory(this)

    init {
        addFactory(factory)
    }

    companion object {
        const val ID = "MoonRunConfiguration"

        /** Gives the registered type. Fails with a clear message if `plugin.xml` does not register the type. */
        fun getInstance(): MoonRunConfigurationType =
            ConfigurationType.CONFIGURATION_TYPE_EP.findExtension(MoonRunConfigurationType::class.java)
                ?: error("plugin.xml must register the configuration type ${MoonRunConfigurationType::class.java.name}")
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
