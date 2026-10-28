package io.github.artptr.moonexplorer.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.GeneralCommandLine
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

    fun testCommandLineWithOnlyEmptyTargetsThrows() {
        val e = Assert.assertThrows(ExecutionException::class.java) { configuration("\"\"").createCommandLine(workspace) { moon } }
        assertEquals(MoonExplorerBundle.message("run.configuration.error.noTarget"), e.message)
    }

    fun testCommandLineForAffectedHasTheOptionAfterTheTargets() {
        val configuration = configuration("web:build api:build").also { it.affected = true }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        // The option --affected can have a value. Before the targets, moon reads the first target as its value.
        assertEquals(listOf("run", "web:build", "api:build", "--affected"), commandLine.parametersList.list)
        assertFalse(commandLine.parametersList.list.any { it.startsWith("--query") })
    }

    fun testCommandLineHasTheArgumentsAfterTwoDashes() {
        val configuration = configuration("web:test").also { it.arguments = "src/app.test.ts" }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        assertEquals(listOf("run", "web:test", "--", "src/app.test.ts"), commandLine.parametersList.list)
    }

    fun testCommandLineHasTheOptionsOfMoonBeforeTwoDashes() {
        // moon gives all arguments after -- to the task command. Thus --affected and --force must be before --.
        val configuration = configuration("web:test").also {
            it.affected = true
            it.force = true
            it.arguments = " \"src/a b.ts\" --watch "
        }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        assertEquals(
            listOf("run", "web:test", "--affected", "--force", "--", "src/a b.ts", "--watch"),
            commandLine.parametersList.list,
        )
    }

    fun testCommandLineForForceHasTheOptionAfterTheTargets() {
        val configuration = configuration("web:build api:build").also { it.force = true }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        assertEquals(listOf("run", "web:build", "api:build", "--force"), commandLine.parametersList.list)
    }

    fun testCommandLineWithBlankArgumentsHasNoTwoDashes() {
        val configuration = configuration().also { it.arguments = "   " }
        assertEquals("", configuration.arguments)
        assertEquals(listOf("run", "web:build"), configuration.createCommandLine(workspace) { moon }.parametersList.list)
    }

    fun testCommandLineHasTheEnvironmentVariablesAndTheSystemEnvironment() {
        val configuration = configuration().also {
            it.environmentVariables = EnvironmentVariablesData.create(mapOf("DEBUG" to "*", "NODE_OPTIONS" to "--inspect=0"), true)
        }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        assertEquals(mapOf("DEBUG" to "*", "NODE_OPTIONS" to "--inspect=0"), commandLine.environment)
        assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, commandLine.parentEnvironmentType)
    }

    fun testCommandLineWithoutTheSystemEnvironmentHasOnlyTheVariablesOfTheConfiguration() {
        val configuration = configuration().also {
            it.environmentVariables = EnvironmentVariablesData.create(mapOf("DEBUG" to "*"), false)
        }
        val commandLine = configuration.createCommandLine(workspace) { moon }
        assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, commandLine.parentEnvironmentType)
        // GeneralCommandLine adds PWD for the working directory. The process does not get other variables, for example PATH.
        assertEquals(mapOf("DEBUG" to "*", "PWD" to workspace.toString()), commandLine.effectiveEnvironment)
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
        assertThrows(RuntimeConfigurationError::class.java) { configuration("\"\" \" \"").checkConfiguration() }
        configuration().checkConfiguration()
    }

    fun testSuggestedNameIsTheTarget() {
        assertEquals("web:build", configuration().suggestedName())
    }

    fun testSuggestedNameTellsTheTargets() {
        assertEquals("build (web, api)", configuration("web:build api:build").suggestedName())
        assertEquals("build (db, ui)", configuration("db:build ui:build").suggestedName())
        assertEquals("web:build, api:audit", configuration("web:build api:audit").suggestedName())
        assertEquals("web:build, affected", configuration().also { it.affected = true }.suggestedName())
        assertEquals("build (web, api), affected", configuration("web:build api:build").also { it.affected = true }.suggestedName())
        assertNull(configuration(" ").suggestedName())
        assertNull(configuration("\"\"").suggestedName())
        // A target without a project ID, for example from a manual change, does not give a name with project IDs.
        assertEquals("test, api:test", configuration("test api:test").suggestedName())
        // The target :build runs build in all projects. It has no project ID.
        assertEquals(":build, web:build", configuration(":build web:build").suggestedName())
    }

    fun testAffectedIsSavedAndLoaded() {
        val element = Element("configuration")
        configuration("web:build api:build").also { it.affected = true }.writeExternal(element)
        val loaded = configuration("")
        loaded.readExternal(element)
        assertEquals(listOf("web:build", "api:build"), loaded.targets)
        assertTrue(loaded.affected)
    }

    fun testArgumentsForceAndEnvironmentAreSavedAndLoaded() {
        val element = Element("configuration")
        configuration("web:test").also {
            it.arguments = "src/app.test.ts --watch"
            it.force = true
            it.environmentVariables = EnvironmentVariablesData.create(mapOf("NODE_OPTIONS" to "--inspect=0", "DEBUG" to "*"), false)
        }.writeExternal(element)
        val loaded = configuration("")
        loaded.readExternal(element)
        assertEquals("src/app.test.ts --watch", loaded.arguments)
        assertTrue(loaded.force)
        assertEquals(mapOf("NODE_OPTIONS" to "--inspect=0", "DEBUG" to "*"), loaded.environmentVariables.envs)
        assertFalse(loaded.environmentVariables.isPassParentEnvs)
    }

    fun testDefaultConfigurationIncludesTheSystemEnvironment() {
        val element = Element("configuration")
        configuration().writeExternal(element)
        val loaded = configuration("")
        loaded.readExternal(element)
        assertEquals("", loaded.arguments)
        assertFalse(loaded.force)
        assertTrue(loaded.environmentVariables.envs.isEmpty())
        assertTrue(loaded.environmentVariables.isPassParentEnvs)
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
            assertFalse(editor.affectedCheckBox.isSelected)
            editor.resetFrom(configuration("web:lint api:lint").also { it.affected = true })
            assertEquals("web:lint api:lint", editor.targetField.text)
            assertTrue(editor.affectedCheckBox.isSelected)
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

    fun testEditorChangesTheArgumentsTheEnvironmentAndForce() {
        val editor = MoonRunConfigurationEditor()
        try {
            editor.component
            val environment = EnvironmentVariablesData.create(mapOf("DEBUG" to "*"), false)
            editor.resetFrom(configuration().also {
                it.arguments = "src/app.test.ts"
                it.environmentVariables = environment
                it.force = true
            })
            assertEquals("src/app.test.ts", editor.argumentsField.text)
            assertEquals(environment, editor.environmentField.data)
            assertTrue(editor.forceCheckBox.isSelected)
            editor.argumentsField.text = "--watch"
            editor.environmentField.data = EnvironmentVariablesData.create(mapOf("NODE_OPTIONS" to "--inspect=0"), true)
            editor.forceCheckBox.isSelected = false
            val changed = configuration()
            editor.applyTo(changed)
            assertEquals("--watch", changed.arguments)
            assertEquals(mapOf("NODE_OPTIONS" to "--inspect=0"), changed.environmentVariables.envs)
            assertTrue(changed.environmentVariables.isPassParentEnvs)
            assertFalse(changed.force)
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
            assertTrue("Comments: $texts", texts.any { "adds them after -- at the end of the command" in it })
            assertTrue("Comments: $texts", texts.any { "Adds the option --force." in it })
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
