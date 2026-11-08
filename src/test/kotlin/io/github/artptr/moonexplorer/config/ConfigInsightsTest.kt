package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.lastLoaded
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
        assertEquals("Projects That Inherit .moon/tasks/all.yml", hint.list.title)
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), hint.list.items.map { it.text })
    }

    fun testItemOfTheInheritedByListOpensTheFileOfTheProject() {
        val item = insights(".moon/tasks/all.yml").inheritedHints().single().list.items.first { it.text == "web" }
        assertEquals(SourceFile(workspacePath("apps/web/moon.yml"), null), item.target)
    }

    fun testItemOfTheInheritedByListShowsTheTaskIdsInTheProject() {
        val items = insights(".moon/tasks/frontend.yml").inheritedHints().single().list.items.associate { it.text to it.secondary }
        // The project web renames typecheck to tsc. The task icons:typecheck is internal, but icons inherits it.
        assertEquals("test, tsc", items["web"])
        assertEquals("test, typecheck", items["icons"])
    }

    fun testOrderOfTheProvidersOnOneLine() {
        assertEquals(listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst), MoonInheritedCodeVisionProvider().relativeOrderings)
        val merged = MoonMergedCodeVisionProvider().relativeOrderings.single() as CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter
        assertEquals(MoonInheritedCodeVisionProvider().id, merged.id)
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

    fun testProjectFileHasTheNumberOfInheritedTasksAboveTheTasksKey() {
        val hint = insights("apps/web/moon.yml").inheritedHints().first()
        assertEquals("3 inherited tasks", hint.text)
        assertEquals("tasks", hint.element.text)
        // The group is the local ID. The secondary text gives the global ID, because web renames typecheck to tsc.
        val typecheck = hint.list.items.single { it.group == "tsc" }
        assertEquals(".moon/tasks/frontend.yml", typecheck.text)
        assertEquals("typecheck", typecheck.secondary)
        assertEquals(SourceFile(workspacePath(".moon/tasks/frontend.yml"), "typecheck"), typecheck.target)
    }

    fun testEntriesOfInheritedTasksHaveHints() {
        val hints = insights("apps/web/moon.yml").inheritedHints().drop(1)
        assertEquals(listOf("Excludes format from .moon/tasks/all.yml", "Renames typecheck from .moon/tasks/frontend.yml to tsc"), hints.map { it.text })
        assertEquals(SourceFile(workspacePath(".moon/tasks/all.yml"), "format"), hints[0].list.items.single().target)
        assertEquals("typecheck", hints[1].element.text)
        // The rename opens the global task key, not the local ID.
        assertEquals(SourceFile(workspacePath(".moon/tasks/frontend.yml"), "typecheck"), hints[1].list.items.single().target)
    }

    fun testIncludeEntryHasAHint() {
        val texts = insights("tools/scripts/moon.yml").inheritedHints().map { it.text }
        assertEquals(listOf("1 inherited task", "Includes lint from .moon/tasks/all.yml"), texts)
    }

    fun testProjectWithoutInheritedTasksHasNoHint() {
        assertEmpty(insights("packages/config/moon.yml").inheritedHints())
    }

    fun testLocalTaskThatMergesHasAHintAndAGutterMark() {
        val insights = insights("apps/web/moon.yml")
        val hint = insights.mergedHints().single()
        assertEquals("Merged with .moon/tasks/all.yml", hint.text)
        assertEquals("lint", hint.element.text)
        assertEquals(SourceFile(workspacePath(".moon/tasks/all.yml"), "lint"), hint.list.items.single().target)
        val mark = insights.gutterMarks().single()
        assertTrue(mark.local)
        assertEquals("Merged with .moon/tasks/all.yml (args: prepend, deps: replace)", mark.tooltip)
        // The list of the icon has one target. Thus a click on the icon opens the global task directly.
        assertEquals(listOf(SourceFile(workspacePath(".moon/tasks/all.yml"), "lint")), mark.list.items.map { it.target })
    }

    fun testListOfARenamedLocalTaskHasTheGlobalId() {
        // The project db renames the global task test to unit and has a local task unit.
        val hint = insights("packages/db/moon.yml").mergedHints().single()
        assertEquals("unit", hint.element.text)
        assertEquals("Global Task 'test'", hint.list.title)
        assertEquals(listOf(SourceFile(workspacePath(".moon/tasks/backend.yml"), "test")), hint.list.items.map { it.target })
    }

    fun testListHasAGroupForEachInheritedTask() {
        // The project api inherits lint from all.yml and from backend.yml. The list opens the two files.
        val api = insights("apps/api/moon.yml").inheritedHints().first()
        assertEquals("${api.list.items.mapTo(HashSet()) { it.group }.size} inherited tasks", api.text)
        val lint = api.list.items.filter { it.group == "lint" }.map { it.target }
        assertEquals(listOf(SourceFile(workspacePath(".moon/tasks/all.yml"), "lint"), SourceFile(workspacePath(".moon/tasks/backend.yml"), "lint")), lint)
    }

    fun testGroupOfATaskFromTwoGlobalTasksOpensEachGlobalTask() {
        // The project worker renames format and test to check.
        val worker = insights("apps/worker/moon.yml").inheritedHints().first()
        assertEquals("${worker.list.items.mapTo(HashSet()) { it.group }.size} inherited tasks", worker.text)
        val check = worker.list.items.filter { it.group == "check" }
        assertEquals(setOf("format", "test"), check.mapTo(HashSet()) { it.target?.taskId })
        assertEquals(setOf("format", "test"), check.mapTo(HashSet()) { it.secondary })
    }

    fun testListOfALocalTaskThatMergesWithTwoGlobalTasksHasGroups() {
        // The data gives worker a local task check. Then check merges with the global tasks format and test.
        val loaded = service.state.value.lastLoaded!!
        val configs = loaded.tasks.taskConfigs.getValue("worker")
        val state = loaded.copy(tasks = loaded.tasks.copy(taskConfigs = loaded.tasks.taskConfigs + ("worker" to configs.copy(ownTasks = configs.ownTasks + "check"))))
        val file = myFixture.configureByText("moon.yml", "tasks:\n  check:\n    command: 'check'\n")
        val insights = ConfigInsights(configSyntax(file)!!, state, workspacePath("apps/worker/moon.yml"))
        val list = insights.mergedHints().single().list
        assertEquals("Global Tasks That Merge with 'check'", list.title)
        assertEquals(setOf("format", "test"), list.items.mapTo(HashSet()) { it.group })
        assertNull("A click on the icon shows the list", directTarget(insights.gutterMarks().single()))
    }

    fun testClickOnTheIconOfALocalTaskWithOneGlobalTaskOpensTheGlobalTask() {
        val local = insights("apps/web/moon.yml").gutterMarks().single()
        assertEquals(SourceFile(workspacePath(".moon/tasks/all.yml"), "lint"), directTarget(local))
        val global = insights(".moon/tasks/all.yml").gutterMarks().first { it.element.text == "lint" }
        assertNull("The icon of a global task shows the list", directTarget(global))
    }

    fun testEntryWithAMissingFileHasADifferentHint() {
        // The load has the layer all.yml for format, but the file is not on the disk.
        val loaded = service.state.value.lastLoaded!!
        val files = loaded.configFiles.copy(inherited = loaded.configFiles.inherited.filterValues { !it.endsWith(".moon/tasks/all.yml") })
        val file = myFixture.configureByText("moon.yml", "workspace:\n  inheritedTasks:\n    exclude: ['format']\n")
        val insights = ConfigInsights(configSyntax(file)!!, loaded.copy(configFiles = files), workspacePath("apps/web/moon.yml"))
        val hint = insights.inheritedHints().last()
        assertEquals("Cannot find the inherited file of format", hint.text)
        assertEmpty(hint.list.items)
    }

    fun testProjectWithoutTheTasksKeyHasTheHintAtTheTop() {
        // The syntax comes from a file without the key tasks. The data comes from the project web.
        val file = myFixture.configureByText("moon.yml", "language: 'typescript'\n")
        val insights = ConfigInsights(configSyntax(file)!!, service.state.value.lastLoaded!!, workspacePath("apps/web/moon.yml"))
        val hint = insights.inheritedHints().single()
        assertEquals("3 inherited tasks", hint.text)
        assertEquals(0, hint.element.textOffset)
    }

    fun testEntryThatMatchesNoGlobalTaskHasAHintWithoutAList() {
        val file = myFixture.configureByText("moon.yml", "workspace:\n  inheritedTasks:\n    exclude: ['nothing']\n")
        val insights = ConfigInsights(configSyntax(file)!!, service.state.value.lastLoaded!!, workspacePath("apps/web/moon.yml"))
        val hint = insights.inheritedHints().last()
        assertEquals("No inherited file of this project has nothing", hint.text)
        assertEmpty(hint.list.items)
    }

    fun testLocalTaskOfAnExcludedGlobalTaskDoesNotMerge() {
        assertFalse(insights("apps/web/moon.yml").mergedHints().any { it.element.text == "format" })
    }

    fun testItemOfARenamedTaskOpensTheLocalTaskKey() {
        // The project db renames the global task test to unit and has a local task unit.
        val hint = insights(".moon/tasks/backend.yml").mergedHints().single { it.element.text == "test" }
        assertEquals(SourceFile(workspacePath("packages/db/moon.yml"), "unit"), hint.list.items.single { it.text == "db" }.target)
    }

    fun testHintTextsHavePluralForms() {
        assertEquals("Merged in 1 project", MoonExplorerBundle.message("hint.mergedIn", 1))
        assertEquals("Merged in 0 projects", MoonExplorerBundle.message("hint.mergedIn", 0))
        assertEquals("Merged in 2 projects", MoonExplorerBundle.message("hint.mergedIn", 2))
        assertEquals("Inherited by 0 projects", MoonExplorerBundle.message("hint.inheritedBy", 0))
        assertEquals("Inherited by 1 project", MoonExplorerBundle.message("hint.inheritedBy", 1))
        assertEquals("1 inherited task", MoonExplorerBundle.message("hint.inheritedTasks", 1))
        assertEquals("2 inherited tasks", MoonExplorerBundle.message("hint.inheritedTasks", 2))
    }

    fun testHintWithAnEmptyListHasNoClickAction() {
        val empty = codeVisionEntry("Inherited by 0 projects", HintList("title", emptyList()), "moon.inherited")
        assertFalse(empty is ClickableTextCodeVisionEntry)
        val one = codeVisionEntry("Inherited by 1 project", HintList("title", listOf(HintItem("web", null))), "moon.inherited")
        assertTrue(one is ClickableTextCodeVisionEntry)
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
