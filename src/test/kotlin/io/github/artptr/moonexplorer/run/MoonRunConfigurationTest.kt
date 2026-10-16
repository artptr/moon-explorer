package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import org.jdom.Element
import org.junit.Assert
import java.nio.file.Path

class MoonRunConfigurationTest : BasePlatformTestCase() {

    private val workspace = Path.of("/work/shop")
    private val moon = Path.of("/work/shop/node_modules/.bin/moon")

    private fun configuration(target: String = "web:build"): MoonRunConfiguration {
        val factory = MoonRunConfigurationType.getInstance().factory
        return (factory.createTemplateConfiguration(project) as MoonRunConfiguration).also { it.target = target }
    }

    fun testTypeIsRegistered() {
        assertTrue(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.any { it is MoonRunConfigurationType })
    }

    fun testCommandLineRunsTheTargetInTheWorkspaceRoot() {
        val commandLine = configuration().createCommandLine(workspace) { moon }
        assertEquals(moon.toString(), commandLine.exePath)
        assertEquals(listOf("run", "web:build"), commandLine.parametersList.list)
        assertEquals(workspace, commandLine.workingDirectory)
    }

    fun testCommandLineKeepsTheColors() {
        val commandLine = configuration().createCommandLine(workspace) { moon }
        assertFalse(commandLine.environment.containsKey("NO_COLOR"))
    }

    fun testCommandLineWithoutWorkspaceThrows() {
        val e = Assert.assertThrows(ExecutionException::class.java) { configuration().createCommandLine(null) { moon } }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.noWorkspace"), e.message)
    }

    fun testCommandLineWithoutMoonThrows() {
        val e = Assert.assertThrows(ExecutionException::class.java) { configuration().createCommandLine(workspace) { null } }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.moonNotFound"), e.message)
    }

    fun testConfigurationWithoutTargetIsNotValid() {
        assertThrows(RuntimeConfigurationError::class.java) { configuration("  ").checkConfiguration() }
        configuration().checkConfiguration()
    }

    fun testSuggestedNameIsTheTarget() {
        assertEquals("web:build", configuration().suggestedName())
    }

    fun testTargetIsSavedAndLoaded() {
        val element = Element("configuration")
        configuration("api:audit").writeExternal(element)
        val loaded = configuration("")
        loaded.readExternal(element)
        assertEquals("api:audit", loaded.target)
    }

    fun testEditorChangesTheTarget() {
        val editor = MoonRunConfigurationEditor()
        try {
            editor.component
            editor.resetFrom(configuration("web:lint"))
            assertEquals("web:lint", editor.targetField.text)
            editor.targetField.text = " web:test "
            val changed = configuration("")
            editor.applyTo(changed)
            assertEquals("web:test", changed.target)
        } finally {
            Disposer.dispose(editor)
        }
    }
}
