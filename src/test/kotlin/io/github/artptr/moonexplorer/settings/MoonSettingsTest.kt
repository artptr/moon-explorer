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
        assertEquals(TaskGrouping.PROJECT, settings.mainGroup)
        assertNull(settings.state.taskGrouping)
    }

    fun testOldTaskNameValueBecomesNoneAndTaskName() {
        val settings = oldGrouping("TASK_NAME")
        assertEquals(TaskCategory.NONE, settings.taskCategory)
        assertEquals(TaskGrouping.TASK_NAME, settings.mainGroup)
        assertNull(settings.state.taskGrouping)
    }

    fun testDefaultValuesAreNoneAndProject() {
        val settings = load("""<component name="MoonExplorerSettings" />""")
        assertEquals(TaskCategory.NONE, settings.taskCategory)
        assertEquals(TaskGrouping.PROJECT, settings.mainGroup)
    }

    fun testNewValuesAreKeptAfterSaveAndLoad() {
        val settings = MoonSettings()
        settings.taskCategory = TaskCategory.LAYER
        settings.mainGroup = TaskGrouping.TASK_NAME
        val xml = JDOMUtil.write(XmlSerializer.serialize(settings.state))
        assertFalse(xml.contains("\"taskGrouping\""))

        val loaded = load(xml)
        assertEquals(TaskCategory.LAYER, loaded.taskCategory)
        assertEquals(TaskGrouping.TASK_NAME, loaded.mainGroup)
    }

    fun testBlankPathIsSavedAsEmpty() {
        val settings = MoonSettings()
        settings.executablePath = "   "
        assertEquals("", settings.executablePath)
        assertNull(settings.state.executablePath)
    }

    // Issue #26 sets the time limit to 60 seconds. If you change the default value, also change this test.
    fun testDefaultTimeoutIsOneMinute() {
        assertEquals(60, MoonSettings.SettingsState().timeoutSeconds)
        assertEquals(60, MoonSettings().timeoutSeconds)
    }

    fun testTimeoutOutsideTheRangeIsNotUsed() {
        val settings = MoonSettings()
        for (value in listOf(0, -5, MoonSettings.MAX_TIMEOUT_SECONDS + 1)) {
            assertThrows(IllegalArgumentException::class.java) { settings.timeoutSeconds = value }
            assertEquals(MoonSettings.DEFAULT_TIMEOUT_SECONDS, settings.state.timeoutSeconds)
            // A stored value outside the range, for example from a changed workspace file, gives the default value.
            settings.state.timeoutSeconds = value
            assertEquals("Stored value $value", MoonSettings.DEFAULT_TIMEOUT_SECONDS, settings.timeoutSeconds)
            settings.state.timeoutSeconds = MoonSettings.DEFAULT_TIMEOUT_SECONDS
        }
    }

    fun testParseTimeoutAcceptsOnlyWholeNumbersInTheRange() {
        assertEquals(1, MoonSettings.parseTimeoutSeconds("1"))
        assertEquals(120, MoonSettings.parseTimeoutSeconds(" 120 "))
        assertEquals(86_400, MoonSettings.parseTimeoutSeconds("86400"))
        for (text in listOf("", " ", "0", "-1", "1.5", "abc", "86401", "99999999999")) {
            assertNull("Text <$text>", MoonSettings.parseTimeoutSeconds(text))
        }
    }
}
