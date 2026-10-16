package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
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
        configurable = MoonSettingsConfigurable(project)
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            settings.executablePath = ""
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
            tasks.queryTasks = { CliResult.Failed("from apply") }
            val moon = fakeMoonExecutable()
            configurable.createComponent()
            configurable.reset()
            pathField().text = moon.toString()
            assertTrue(configurable.isModified)
            configurable.apply()
            assertEquals(moon.toString(), settings.executablePath)
            tasks.awaitState(TasksState.MoonError("from apply"))
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
}
