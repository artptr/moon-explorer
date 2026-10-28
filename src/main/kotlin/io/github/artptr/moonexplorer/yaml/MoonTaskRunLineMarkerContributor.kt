package io.github.artptr.moonexplorer.yaml

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import io.github.artptr.moonexplorer.ui.taskGutterInfo

/**
 * Shows a run icon in the gutter for each task key in a YAML configuration file of moon: `moon.yml`, `moon.yaml`, and the files in `.moon/tasks/`.
 * The contributor uses only the data that the plugin loaded before. It does not start moon. A task that is not in the loaded data has no icon.
 * After a load, [io.github.artptr.moonexplorer.ui.RunMarkersRefresher] updates the icons.
 */
class MoonTaskRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {

    override fun getInfo(element: PsiElement): Info? = taskKeyTargetsAt(element)?.let(::taskGutterInfo)
}
