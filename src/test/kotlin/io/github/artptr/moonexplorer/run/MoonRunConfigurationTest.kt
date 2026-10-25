package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import org.jdom.Element
import org.junit.Assert
import java.nio.file.Path
import javax.swing.JEditorPane

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

    fun testCommandLineRunsAllTargets() {
        val commandLine = configuration("web:build  api:build\tutils:build").createCommandLine(workspace) { moon }
        assertEquals(listOf("run", "web:build", "api:build", "utils:build"), commandLine.parametersList.list)
    }

    fun testCommandLineForAffectedHasTheOptionAfterTheTargets() {
        val configuration = configuration("web:build api:build").also { it.affected = true }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        // The option --affected can have a value. Before the targets, moon reads the first target as its value.
        assertEquals(listOf("run", "web:build", "api:build", "--affected"), commandLine.parametersList.list)
        assertFalse(commandLine.parametersList.list.any { it.startsWith("--query") })
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

    fun testSuggestedNameTellsTheTargets() {
        assertEquals("build (2 projects)", configuration("web:build api:build").suggestedName())
        assertEquals("2 tasks", configuration("web:build api:audit").suggestedName())
        assertEquals("web:build, affected", configuration().also { it.affected = true }.suggestedName())
        assertEquals("build (2 projects), affected", configuration("web:build api:build").also { it.affected = true }.suggestedName())
        assertNull(configuration(" ").suggestedName())
    }

    fun testAffectedIsSavedAndLoaded() {
        val element = Element("configuration")
        configuration("web:build api:build").also { it.affected = true }.writeExternal(element)
        val loaded = configuration("")
        loaded.readExternal(element)
        assertEquals(listOf("web:build", "api:build"), loaded.targets)
        assertTrue(loaded.affected)
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
            editor.affectedCheckBox.isSelected = true
            editor.applyTo(changed)
            assertEquals("web:test", changed.target)
            assertTrue(changed.affected)
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testEditorCommentShowsTheFullText() {
        // The comment is HTML. Text in angle brackets, for example <target>, does not show.
        val editor = MoonRunConfigurationEditor()
        try {
            val texts = UIUtil.findComponentsOfType(editor.component, JEditorPane::class.java)
                .map { it.document.getText(0, it.document.length) }
            assertTrue("Comments: $texts", texts.any { "moon run with these targets in the workspace root" in it })
        } finally {
            Disposer.dispose(editor)
        }
    }

    fun testRunWithoutWorkspaceStopsWithAMessageAndStartsNoProcess() {
        // The light test project is not a moon workspace. Thus startProcess stops before it starts a process.
        val configuration = configuration()
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(project, executor, configuration).build()
        val state = configuration.getState(executor, environment)
        assertInstanceOf(state, CommandLineState::class.java)
        val runner = ProgramRunner.getRunner(executor.id, configuration) ?: error("No runner for the moon configuration")
        val e = Assert.assertThrows(ExecutionException::class.java) { state.execute(executor, runner) }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.noWorkspace"), e.message)
    }
}
