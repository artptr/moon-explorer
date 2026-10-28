package io.github.artptr.moonexplorer.yaml

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import io.github.artptr.moonexplorer.model.TaskKeyTargets
import io.github.artptr.moonexplorer.run.MoonRunConfiguration
import io.github.artptr.moonexplorer.run.MoonRunConfigurationType
import io.github.artptr.moonexplorer.run.configurationName

/**
 * Makes a moon run configuration for a task key in the `moon.yml` file of a project. The configuration runs `<project>:<task>`.
 * Thus the gutter icon and the context menu of the key show the item **Run**.
 * The producer uses a configuration only if it has the same target and does not use the affected option. The tool window does the same.
 * A key in `.moon/tasks/` gets no configuration. Its gutter popup contains the actions for the projects.
 */
class MoonTaskRunConfigurationProducer : LazyRunConfigurationProducer<MoonRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = MoonRunConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: MoonRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val target = singleTarget(context) ?: return false
        configuration.targets = listOf(target)
        configuration.affected = false
        configuration.name = configurationName(configuration.targets, affected = false)
        return true
    }

    override fun isConfigurationFromContext(configuration: MoonRunConfiguration, context: ConfigurationContext): Boolean {
        val target = singleTarget(context) ?: return false
        return configuration.targets == listOf(target) && !configuration.affected
    }

    private fun singleTarget(context: ConfigurationContext): String? =
        (context.psiLocation?.let(::taskKeyTargetsAt) as? TaskKeyTargets.Single)?.target
}
