package io.github.artptr.moonexplorer.run

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project

/** Removes all moon run configurations of [project]. The light project is the same for all tests, thus tests call this in tearDown. */
fun removeMoonConfigurations(project: Project) {
    val runManager = RunManager.getInstance(project)
    runManager.getConfigurationSettingsList(MoonRunConfigurationType.getInstance()).forEach(runManager::removeConfiguration)
}
