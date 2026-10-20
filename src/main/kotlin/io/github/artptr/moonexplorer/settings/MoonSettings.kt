package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import io.github.artptr.moonexplorer.model.TaskCategory
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
        var taskCategory by enum(TaskCategory.NONE)
        var mainGroup by enum(TaskGrouping.PROJECT)
        var timeoutSeconds by property(DEFAULT_TIMEOUT_SECONDS)

        /**
         * The group mode of version 0.1.0: `PROJECT`, `TASK_NAME`, or `STACK`. [loadState] changes it to [taskCategory] and [mainGroup].
         * The value is text, thus the plugin can read the old value `STACK`.
         * Do not change the name of this property. The name is the name of the option in the workspace file.
         */
        var taskGrouping by string()
    }

    override fun loadState(state: SettingsState) {
        super.loadState(state)
        val oldGrouping = state.taskGrouping ?: return
        when (oldGrouping) {
            "STACK" -> {
                state.taskCategory = TaskCategory.STACK
                state.mainGroup = TaskGrouping.PROJECT
            }
            "TASK_NAME" -> state.mainGroup = TaskGrouping.TASK_NAME
            else -> state.mainGroup = TaskGrouping.PROJECT
        }
        // Do not write the old value again.
        state.taskGrouping = null
    }

    /** The path to the `moon` executable. If the value is empty, the plugin finds moon automatically. */
    var executablePath: String
        get() = state.executablePath.orEmpty()
        set(value) {
            state.executablePath = value.trim().ifEmpty { null }
        }

    /** The category nodes at the top of the tree of the Moon tool window. */
    var taskCategory: TaskCategory
        get() = state.taskCategory
        set(value) {
            state.taskCategory = value
        }

    /** The main group of the tree of the Moon tool window. It sets the structure in each category node. */
    var mainGroup: TaskGrouping
        get() = state.mainGroup
        set(value) {
            state.mainGroup = value
        }

    /**
     * The time limit to load the tasks, in seconds. After this time, the plugin stops `moon query projects`.
     * The time limit does not apply to `moon run`.
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
