package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.github.artptr.moonexplorer.model.TaskGrouping

/**
 * The settings of the plugin for the IDE project, and the state of the Moon tool window.
 * The settings are in the workspace file of the IDE project. Thus they are not in version control.
 */
@Service(Service.Level.PROJECT)
@State(name = "MoonExplorerSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MoonSettings : SimplePersistentStateComponent<MoonSettings.SettingsState>(SettingsState()) {

    class SettingsState : BaseState() {
        var executablePath by string()
        var taskGrouping by enum(TaskGrouping.PROJECT)
    }

    /** The path to the `moon` executable. If the value is empty, the plugin finds moon automatically. */
    var executablePath: String
        get() = state.executablePath.orEmpty()
        set(value) {
            state.executablePath = value.trim().ifEmpty { null }
        }

    /** How the tree of the Moon tool window puts the tasks into groups. */
    var taskGrouping: TaskGrouping
        get() = state.taskGrouping
        set(value) {
            state.taskGrouping = value
        }
}
