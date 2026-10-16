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
        var timeoutSeconds by property(DEFAULT_TIMEOUT_SECONDS)
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

    /**
     * The time limit for a moon command, in seconds. After this time, the plugin stops the moon process.
     * If the value in the state is not in [TIMEOUT_RANGE], the plugin uses [DEFAULT_TIMEOUT_SECONDS].
     * This occurs only if a person changes the workspace file manually. The setter accepts only values in [TIMEOUT_RANGE].
     */
    var timeoutSeconds: Int
        get() = state.timeoutSeconds.takeIf { it in TIMEOUT_RANGE } ?: DEFAULT_TIMEOUT_SECONDS
        set(value) {
            require(value in TIMEOUT_RANGE) { "The time limit $value is not in $TIMEOUT_RANGE" }
            state.timeoutSeconds = value
        }

    companion object {
        /** The default time limit. Issue #26 sets this value. */
        const val DEFAULT_TIMEOUT_SECONDS = 60

        /** The maximum time limit is 24 hours. In milliseconds, this value is less than [Int.MAX_VALUE]. */
        const val MAX_TIMEOUT_SECONDS = 86_400

        /** The time limits that the settings page accepts. */
        val TIMEOUT_RANGE = 1..MAX_TIMEOUT_SECONDS

        /** Converts the text of the settings field to a time limit. Gives null if the text is not a whole number in [TIMEOUT_RANGE]. */
        fun parseTimeoutSeconds(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in TIMEOUT_RANGE }
    }
}
