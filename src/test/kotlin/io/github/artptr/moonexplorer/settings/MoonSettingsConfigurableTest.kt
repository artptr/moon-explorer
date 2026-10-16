package io.github.artptr.moonexplorer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.testData.fakeMoonExecutable
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
}
