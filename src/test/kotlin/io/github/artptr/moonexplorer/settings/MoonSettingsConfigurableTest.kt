package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.fakeMoonExecutable
import kotlinx.coroutines.runBlocking
import java.io.File

class MoonSettingsConfigurableTest : BasePlatformTestCase() {

    private lateinit var settings: MoonSettings
    private lateinit var configurable: MoonSettingsConfigurable

    override fun setUp() {
        super.setUp()
        settings = project.service<MoonSettings>()
        settings.executablePath = ""
        settings.timeoutSeconds = MoonSettings.DEFAULT_TIMEOUT_SECONDS
        configurable = MoonSettingsConfigurable(project)
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            settings.executablePath = ""
            settings.timeoutSeconds = MoonSettings.DEFAULT_TIMEOUT_SECONDS
        } finally {
            super.tearDown()
        }
    }

    private fun awaitFoundText(expected: String) {
        PlatformTestUtil.waitWithEventsDispatching(
            { "Expected <$expected>, but the field shows <${configurable.foundField.text}>" },
            { configurable.foundField.text == expected },
            10,
        )
    }

    fun testSettingsPageIsRegistered() {
        val registered = Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
            .any { it.id == MoonSettingsConfigurable.ID && it.parentId == "tools" }
        assertTrue("The settings page must be in Settings | Tools", registered)
    }

    fun testPageShowsThePathThatThePluginFound() {
        val moon = fakeMoonExecutable()
        settings.executablePath = moon.toString()
        configurable.createComponent()
        configurable.reset()
        awaitFoundText(moon.toString())
    }

    fun testPageShowsTheSearchTextBeforeTheResult() {
        configurable.createComponent()
        configurable.reset()
        // The search runs on a background thread. Thus reset returns before the result is available.
        assertEquals(MoonExplorerBundle.message("settings.found.searching"), configurable.foundField.text)
    }

    fun testPageIsModifiedWhenTheSavedPathChanges() {
        val path = File(FileUtil.getTempDirectory(), "custom-moon").path
        configurable.createComponent()
        configurable.reset()
        settings.executablePath = path
        assertTrue(configurable.isModified)
        configurable.reset()
        assertEquals(path, settings.executablePath)
    }

    fun testBlankPathIsSavedAsEmpty() {
        settings.executablePath = "   "
        assertEquals("", settings.executablePath)
        assertNull(settings.state.executablePath)
    }

    fun testPageFindsMoonWithTheTextOfTheFieldBeforeApply() {
        val moon = fakeMoonExecutable()
        configurable.createComponent()
        configurable.reset()
        configurable.updateFoundPath(moon.toString(), delayMillis = 0)
        awaitFoundText(moon.toString())
        assertEquals("The saved path must not change before Apply", "", settings.executablePath)
    }

    private fun pathField(): TextFieldWithBrowseButton =
        UIUtil.findComponentOfType(configurable.createComponent(), TextFieldWithBrowseButton::class.java)
            ?: error("The settings page has no path field")

    fun testApplySavesThePathAndLoadsTheTasksAgain() {
        val tasks = project.service<MoonTasksService>()
        tasks.prepareForTests()
        try {
            tasks.queryProjects = { CliResult.Failed("from apply") }
            val moon = fakeMoonExecutable()
            configurable.createComponent()
            configurable.reset()
            pathField().text = moon.toString()
            assertTrue(configurable.isModified)
            configurable.apply()
            assertEquals(moon.toString(), settings.executablePath)
            tasks.awaitState(TasksState.MoonFailed(CliResult.Failed("from apply")))
        } finally {
            runBlocking { tasks.resetForTests() }
        }
    }

    fun testPathThatIsNotValidShowsAnError() {
        configurable.createComponent()
        configurable.reset()
        pathField().text = "bad\u0000path"
        // The page checks the path on input. Thus the user sees the error before Apply.
        val panel = configurable.createComponent() as DialogPanel
        val errors = panel.validationsOnInput.values.flatten().mapNotNull { it.validate() }
        assertEquals(listOf(MoonExplorerBundle.message("settings.executablePath.invalid")), errors.map { it.message })
        pathField().text = "/tools/moon"
        assertEmpty(panel.validationsOnInput.values.flatten().mapNotNull { it.validate() })
    }

    fun testNewSearchReplacesTheResultOfThePreviousSearch() {
        val first = fakeMoonExecutable()
        val second = fakeMoonExecutable()
        configurable.createComponent()
        val slow = configurable.updateFoundPath(first.toString(), delayMillis = 1_000)
        configurable.updateFoundPath(second.toString(), delayMillis = 0)
        awaitFoundText(second.toString())
        PlatformTestUtil.waitWithEventsDispatching("The first search must stop", { slow.isCompleted }, 10)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals(second.toString(), configurable.foundField.text)
    }

    // Issue #26 sets the time limit to 60 seconds. If you change the default value, also change this test.
    fun testDefaultTimeoutIsOneMinute() {
        assertEquals(60, MoonSettings.SettingsState().timeoutSeconds)
        assertEquals(60, MoonSettings().timeoutSeconds)
    }

    fun testStoredTimeoutOutsideTheRangeGivesTheDefault() {
        for (value in listOf(0, -5, MoonSettings.MAX_TIMEOUT_SECONDS + 1)) {
            settings.state.timeoutSeconds = value
            assertEquals("Stored value $value", MoonSettings.DEFAULT_TIMEOUT_SECONDS, settings.timeoutSeconds)
        }
    }

    fun testParseTimeoutAcceptsWholeNumbersInTheRange() {
        assertEquals(1, MoonSettings.parseTimeoutSeconds("1"))
        assertEquals(120, MoonSettings.parseTimeoutSeconds(" 120 "))
        assertEquals(86_400, MoonSettings.parseTimeoutSeconds("86400"))
    }

    fun testParseTimeoutRejectsOtherText() {
        for (text in listOf("", " ", "0", "-1", "1.5", "abc", "86401", "99999999999")) {
            assertNull("Text <$text>", MoonSettings.parseTimeoutSeconds(text))
        }
    }

    fun testPageShowsTheSavedTimeout() {
        settings.timeoutSeconds = 300
        configurable.createComponent()
        configurable.reset()
        assertEquals("300", configurable.timeoutField.text)
    }

    fun testTimeoutThatIsNotValidShowsAnError() {
        val panel = configurable.createComponent() as DialogPanel
        configurable.reset()
        val expected = MoonExplorerBundle.message("settings.timeout.invalid", "1", "86400")
        for (text in listOf("0", "abc", "90000")) {
            configurable.timeoutField.text = text
            val errors = panel.validationsOnInput.values.flatten().mapNotNull { it.validate() }
            assertEquals("Text <$text>", listOf(expected), errors.map { it.message })
        }
        configurable.timeoutField.text = "90"
        assertEmpty(panel.validationsOnInput.values.flatten().mapNotNull { it.validate() })
    }

    fun testApplyRejectsATimeoutThatIsNotValid() {
        configurable.createComponent()
        configurable.reset()
        configurable.timeoutField.text = "0"
        assertTrue(configurable.isModified)
        assertThrows(ConfigurationException::class.java) { configurable.apply() }
        assertEquals(MoonSettings.DEFAULT_TIMEOUT_SECONDS, settings.timeoutSeconds)
    }

    fun testApplySavesTheTimeoutAndLoadsTheTasksAgain() {
        val tasks = project.service<MoonTasksService>()
        tasks.prepareForTests()
        try {
            tasks.queryProjects = { CliResult.Failed("from timeout") }
            configurable.createComponent()
            configurable.reset()
            configurable.timeoutField.text = "300"
            configurable.apply()
            assertEquals(300, settings.timeoutSeconds)
            assertFalse(configurable.isModified)
            tasks.awaitState(TasksState.MoonFailed(CliResult.Failed("from timeout")))
        } finally {
            runBlocking { tasks.resetForTests() }
        }
    }

    fun testSetterRejectsATimeoutOutsideTheRange() {
        for (value in listOf(0, -5, MoonSettings.MAX_TIMEOUT_SECONDS + 1)) {
            assertThrows(IllegalArgumentException::class.java) { settings.timeoutSeconds = value }
        }
        assertEquals(MoonSettings.DEFAULT_TIMEOUT_SECONDS, settings.state.timeoutSeconds)
    }

    fun testApplyShowsTheSavedTimeoutWithoutSpaces() {
        val tasks = project.service<MoonTasksService>()
        tasks.prepareForTests()
        try {
            configurable.createComponent()
            configurable.reset()
            configurable.timeoutField.text = " 120 "
            configurable.apply()
            assertEquals(120, settings.timeoutSeconds)
            assertEquals("120", configurable.timeoutField.text)
            assertFalse("The page must not be modified after apply", configurable.isModified)
        } finally {
            runBlocking { tasks.resetForTests() }
        }
    }

    fun testCommentShowsTheDefaultTimeout() {
        val comment = MoonExplorerBundle.message("settings.timeout.comment", MoonSettings.DEFAULT_TIMEOUT_SECONDS.toString())
        assertTrue(comment.contains("The default value is 60."))
        // The comment must tell that the time limit is not for moon run.
        assertTrue(comment.contains("The time limit does not apply to the tasks that you run."))
    }
}
