package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.TaskConfigs
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConfigInsightsTest : BasePlatformTestCase() {

    private val service get() = project.service<MoonTasksService>()

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
        service.loadRecorded()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    private fun insights(path: String): ConfigInsights {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(path))
        return configInsights(myFixture.file) ?: error("No insights for $path")
    }

    private fun workspacePath(path: String) = TestData.workspaceRoot.resolve(path)

    fun testFileHasTheInheritedByHintAtTheTop() {
        val hint = insights(".moon/tasks/all.yml").inheritedHints().single()
        assertEquals("Inherited by 9 projects", hint.text)
        assertEquals(0, hint.element.textOffset)
        assertEquals("Projects That Inherit all.yml", hint.list.title)
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), hint.list.items.map { it.text })
    }

    fun testItemOfTheInheritedByListOpensTheFileOfTheProject() {
        val item = insights(".moon/tasks/all.yml").inheritedHints().single().list.items.first { it.text == "web" }
        assertEquals(SourceFile(workspacePath("apps/web/moon.yml"), null), item.target)
    }

    fun testCountUsesTheFiltersOfTheProjects() {
        // The project web inherits frontend.yml through its stack. Only frontend projects are in the list.
        val items = insights(".moon/tasks/frontend.yml").inheritedHints().single().list.items.map { it.text }
        assertEquals(listOf("admin", "icons", "ui", "utils", "web"), items)
    }

    fun testGlobalTaskThatMergesHasAHintWithTheStrategies() {
        val hint = insights(".moon/tasks/all.yml").mergedHints().single { it.element.text == "lint" }
        assertEquals("Merged in 1 project", hint.text)
        assertEquals("lint", hint.element.text)
        val item = hint.list.items.single()
        assertEquals("web (args: prepend, deps: replace)", item.text)
        assertEquals(SourceFile(workspacePath("apps/web/moon.yml"), "lint"), item.target)
    }

    fun testExcludedTaskWithALocalTaskOfTheSameIdIsNotInTheList() {
        // The project web excludes format and has a local task format. The internal task icons:format merges.
        val hint = insights(".moon/tasks/all.yml").mergedHints().single { it.element.text == "format" }
        assertEquals(listOf("icons (args: replace)"), hint.list.items.map { it.text })
    }

    fun testGutterMarkHasTheSameList() {
        val insights = insights(".moon/tasks/all.yml")
        val mark = insights.gutterMarks().single { it.element.text == "lint" }
        assertEquals("Merged in 1 project", mark.tooltip)
        assertEquals(insights.mergedHints().single { it.element.text == "lint" }.list, mark.list)
    }

    fun testHintTextsHavePluralForms() {
        assertEquals("Merged in 1 project", MoonExplorerBundle.message("hint.mergedIn", 1))
        assertEquals("Merged in 2 projects", MoonExplorerBundle.message("hint.mergedIn", 2))
        assertEquals("Inherited by 0 projects", MoonExplorerBundle.message("hint.inheritedBy", 0))
        assertEquals("Inherited by 1 project", MoonExplorerBundle.message("hint.inheritedBy", 1))
    }

    fun testHintWithAnEmptyListHasNoClickAction() {
        val tasks = WorkspaceTasks(emptyList(), taskConfigs = mapOf("a" to TaskConfigs(layers = mapOf("lint" to listOf(".moon/tasks/all.yml")))))
        val state = TestData.loadedState(tasks, taskGraph = null)
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(".moon/tasks/all.yml"))
        val hint = ConfigInsights(configSyntax(myFixture.file)!!, state, workspacePath(".moon/tasks/all.yml")).inheritedHints().single()
        assertEquals("Inherited by 0 projects", hint.text)
        assertEmpty(hint.list.items)
    }

    fun testProjectFileHasNoInsightsInThisPart() {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile("apps/web/moon.yml"))
        assertNull(configInsights(myFixture.file))
    }

    fun testStrategiesAreOnlyInTheTextIfTheyExist() {
        assertEquals("web", withStrategies("web", emptyMap()))
        assertEquals("web (args: replace)", withStrategies("web", mapOf("args" to "replace")))
    }

    fun testFileOutsideTheLoadHasNoInsights() {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(".moon/workspace.yml"))
        assertNull(configInsights(myFixture.file))
    }

    fun testProvidersGiveTheHintsInTheEditor() {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(".moon/tasks/all.yml"))
        val inherited = MoonInheritedCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file)
        val merged = MoonMergedCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file)
        assertEquals(listOf("Inherited by 9 projects"), inherited.map { (it.second as ClickableTextCodeVisionEntry).longPresentation })
        assertEquals(listOf("Merged in 1 project", "Merged in 1 project"), merged.map { (it.second as ClickableTextCodeVisionEntry).longPresentation })
        assertEquals(listOf("lint", "format"), merged.map { myFixture.file.findElementAt(it.first.startOffset)?.text })
    }

    fun testExtensionsAreRegistered() {
        val providers = ExtensionPointName<DaemonBoundCodeVisionProvider>(DaemonBoundCodeVisionProvider.EP_NAME).extensionList
        assertTrue(providers.any { it is MoonInheritedCodeVisionProvider })
        assertTrue(providers.any { it is MoonMergedCodeVisionProvider })
        val settings = ExtensionPointName<CodeVisionGroupSettingProvider>("com.intellij.config.codeVisionGroupSettingProvider").extensionList
        assertEquals(MOON_CODE_VISION_GROUP, settings.first { it is MoonCodeVisionGroupSettingProvider }.groupId)
    }

    fun testNoTextUsesTheWordUsages() {
        val bundle = java.util.ResourceBundle.getBundle("messages.MoonExplorerBundle")
        val hintKeys = bundle.keySet().filter { it.startsWith("hint.") || it.startsWith("codeVision.") || it.startsWith("gutter.") }
        hintKeys.forEach { assertFalse(it, bundle.getString(it).contains("usage", ignoreCase = true)) }
    }

    fun testStepOpensTheTargetOfTheChosenItem() {
        val target = SourceFile(workspacePath("apps/web/moon.yml"), "lint")
        val chosen = mutableListOf<SourceFile>()
        val step = HintListStep(HintList("title", listOf(HintItem("web", target), HintItem("api", null)))) { chosen += it }
        assertTrue(step.isSelectable(step.values[0]))
        assertFalse(step.isSelectable(step.values[1]))
        step.onChosen(step.values[0], true)
        step.finalRunnable?.run()
        assertEquals(listOf(target), chosen)
    }

    fun testStepHasASeparatorAboveEachGroup() {
        val items = listOf(HintItem("a", null, "ui:build"), HintItem("b", null, "ui:build"), HintItem("a", null, "web:build"))
        val step = HintListStep(HintList("title", items)) { }
        assertEquals(listOf("ui:build", null, "web:build"), items.map { step.getSeparatorAbove(it)?.text })
    }

    fun testOpensTheTaskKeyWithTheConverter() {
        val text = TestData.workspaceText("apps/web/moon.yml")
        // The test runs on the EDT. Thus it dispatches the events until the coroutine completes. The function needs the EDT at its end.
        val job = service.scope.launch { openConfigFile(project, SourceFile(workspacePath("apps/web/moon.yml"), "lint")) }
        PlatformTestUtil.waitWithEventsDispatching("The file did not open", { job.isCompleted }, 10)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(text.indexOf("\n  lint:") + 3, editor.caretModel.offset)
    }
}
