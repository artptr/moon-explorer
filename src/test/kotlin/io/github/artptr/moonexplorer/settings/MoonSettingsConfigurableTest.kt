package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.MoonFailure
import io.github.artptr.moonexplorer.model.MoonTasksServiceTestCase
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.awaitState
import io.github.artptr.moonexplorer.testData.fakeMoonExecutable
import java.io.File

/** Apply loads the tasks again. Thus the test uses [MoonTasksServiceTestCase], which also resets the settings in tearDown. */
class MoonSettingsConfigurableTest : MoonTasksServiceTestCase() {

    private lateinit var settings: MoonSettings
    private lateinit var configurable: MoonSettingsConfigurable

    override fun setUp() {
        super.setUp()
        settings = project.service<MoonSettings>()
        settings.resetForTests()
        configurable = MoonSettingsConfigurable(project)
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
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
        service.queryProjects = { CliResult.Failed("from apply") }
        val moon = fakeMoonExecutable()
        configurable.createComponent()
        configurable.reset()
        pathField().text = moon.toString()
        assertTrue(configurable.isModified)
        configurable.apply()
        assertEquals(moon.toString(), settings.executablePath)
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("from apply")))
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

    fun testApplySavesTheTimeoutWithoutSpacesAndLoadsTheTasksAgain() {
        service.queryProjects = { CliResult.Failed("from timeout") }
        configurable.createComponent()
        configurable.reset()
        configurable.timeoutField.text = " 300 "
        configurable.apply()
        assertEquals(300, settings.timeoutSeconds)
        assertEquals("300", configurable.timeoutField.text)
        assertFalse("The page must not be modified after apply", configurable.isModified)
        service.awaitState(TasksState.MoonFailed(MoonFailure.Error("from timeout")))
    }

}
