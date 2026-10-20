package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping

class MoonSettingsTest : BasePlatformTestCase() {

    /** Reads [xml] in the format of the workspace file. Then it gives the settings with the loaded state. */
    private fun load(xml: String): MoonSettings {
        val state = XmlSerializer.deserialize(JDOMUtil.load(xml), MoonSettings.SettingsState::class.java)
        return MoonSettings().apply { loadState(state) }
    }

    private fun oldGrouping(value: String) = load("""<component name="MoonExplorerSettings"><option name="taskGrouping" value="$value" /></component>""")

    fun testOldStackValueBecomesStackAndProject() {
        val settings = oldGrouping("STACK")
        assertEquals(TaskCategory.STACK, settings.taskCategory)
        assertEquals(TaskGrouping.PROJECT, settings.taskGrouping)
        assertNull(settings.state.taskGrouping)
    }

    fun testOldTaskNameValueBecomesNoneAndTaskName() {
        val settings = oldGrouping("TASK_NAME")
        assertEquals(TaskCategory.NONE, settings.taskCategory)
        assertEquals(TaskGrouping.TASK_NAME, settings.taskGrouping)
        assertNull(settings.state.taskGrouping)
    }

    fun testDefaultValuesAreNoneAndProject() {
        val settings = load("""<component name="MoonExplorerSettings" />""")
        assertEquals(TaskCategory.NONE, settings.taskCategory)
        assertEquals(TaskGrouping.PROJECT, settings.taskGrouping)
    }

    fun testNewValuesAreKeptAfterSaveAndLoad() {
        val settings = MoonSettings()
        settings.taskCategory = TaskCategory.LAYER
        settings.taskGrouping = TaskGrouping.TASK_NAME
        val xml = JDOMUtil.write(XmlSerializer.serialize(settings.state))
        assertFalse(xml.contains("\"taskGrouping\""))

        val loaded = load(xml)
        assertEquals(TaskCategory.LAYER, loaded.taskCategory)
        assertEquals(TaskGrouping.TASK_NAME, loaded.taskGrouping)
    }
}
