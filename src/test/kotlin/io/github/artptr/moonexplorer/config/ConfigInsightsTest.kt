package io.github.artptr.moonexplorer.config

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.cli.CliResult
import io.github.artptr.moonexplorer.model.*
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.launch

class ConfigInsightsTest : MoonTasksServiceTestCase() {

    override fun setUp() {
        super.setUp()
        service.loadRecorded()
    }

    /** Gives the insights of the workspace file [path]. If [state] is not null, the insights use [state] and not the loaded data. */
    private fun insights(path: String, state: TasksState.Loaded? = null): ConfigInsights {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(path))
        if (state != null) return ConfigInsights(configSyntax(myFixture.file)!!, state, TestData.workspacePath(path))
        return configInsights(myFixture.file) ?: error("No insights for $path")
    }

    /**
     * Gives a state with the projects of [sources] (ID to directory) and the task graph [graph].
     * [inherits] gives the tasks of each project: the local ID to the global ID in `all.yml`.
     */
    private fun inheritingState(sources: Map<String, String>, inherits: Map<String, Map<String, String>>, graph: Map<String, Set<String>>): TasksState.Loaded {
        val tasks = WorkspaceTasks(
            projects = inherits.map { (id, ids) -> MoonProject(id, ids.keys.map { MoonTask(it, "$id:$it", null) }) },
            sources = sources.map { (id, dir) -> ProjectSource(id, dir) },
            taskConfigs = inherits.mapValues { (_, ids) -> TaskConfigs(inheritedLayers = ids.mapValues { (_, globalId) -> listOf(InheritedLayer(globalId, ALL)) }) },
        )
        return TestData.loadedState(tasks, taskGraph = TaskGraph(graph))
    }

    private val loaded get() = service.state.value.lastLoaded!!

    /** Gives the insights of a file with [text], with the data of the configuration file [path] in [state]. */
    private fun insightsOf(text: String, path: String, state: TasksState.Loaded = loaded): ConfigInsights =
        ConfigInsights(configSyntax(myFixture.configureByText("moon.yml", text))!!, state, TestData.workspacePath(path))

    fun testFileHasTheInheritedByHintAtTheTop() {
        val hint = insights(".moon/tasks/all.yml").inheritedHints().single()
        assertEquals("Inherited by 9 projects", hint.text)
        assertEquals(0, hint.element.textOffset)
        assertEquals("Projects That Inherit .moon/tasks/all.yml", hint.list.title)
        assertEquals(listOf("admin", "api", "db", "icons", "scripts", "ui", "utils", "web", "worker"), hint.list.items.map { it.text })
    }

    fun testItemOfTheInheritedByListOpensTheFileOfTheProject() {
        val item = insights(".moon/tasks/all.yml").inheritedHints().single().list.items.first { it.text == "web" }
        assertEquals(SourceFile(TestData.workspacePath("apps/web/moon.yml"), null), item.target)
    }

    fun testItemOfTheInheritedByListShowsTheTaskIdsInTheProject() {
        val items = insights(".moon/tasks/frontend.yml").inheritedHints().single().list.items.associate { it.text to it.secondary }
        // The project web renames typecheck to tsc. The task icons:typecheck is internal, but icons inherits it.
        assertEquals("test, tsc", items["web"])
        assertEquals("test, typecheck", items["icons"])
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
        assertEquals(SourceFile(TestData.workspacePath("apps/web/moon.yml"), "lint"), item.target)
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
        assertEquals(SourceFile(TestData.workspacePath(".moon/tasks/frontend.yml"), "typecheck"), typecheck.target)
    }

    fun testProjectWithoutInheritedTasksHasNoHints() {
        // The project config has no tasks and inherits no task. The insights exist, because the inspection uses the layers of the project.
        val insights = insights("packages/config/moon.yml")
        assertEmpty(insights.inheritedHints())
        assertEmpty(insights.mergedHints())
        assertEquals(listOf("format", "lint"), insights.globalTaskIds())
    }

    fun testLocalTaskThatMergesHasAHintAndAGutterMark() {
        val insights = insights("apps/web/moon.yml")
        val hint = insights.mergedHints().single()
        assertEquals("Merged with all.yml", hint.text)
        assertEquals("lint", hint.element.text)
        assertEquals(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "lint"), hint.list.items.single().target)
        val mark = insights.gutterMarks().single()
        assertTrue(mark.local)
        assertEquals("Merged with all.yml (args: prepend, deps: replace)", mark.tooltip)
        // The list of the icon has one target. Thus a click on the icon opens the global task directly.
        assertEquals(listOf(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "lint")), mark.list.items.map { it.target })
    }

    fun testListOfARenamedLocalTaskHasTheGlobalId() {
        // The project db renames the global task test to unit and has a local task unit.
        val hint = insights("packages/db/moon.yml").mergedHints().single()
        assertEquals("unit", hint.element.text)
        assertEquals("Global Task 'test'", hint.list.title)
        assertEquals(listOf(SourceFile(TestData.workspacePath(".moon/tasks/backend.yml"), "test")), hint.list.items.map { it.target })
    }

    fun testListHasAGroupForEachInheritedTask() {
        // The project api inherits lint from all.yml and from backend.yml. The list opens the two files.
        val api = insights("apps/api/moon.yml").inheritedHints().first()
        assertEquals("4 inherited tasks", api.text)
        assertEquals(4, api.list.items.mapTo(HashSet()) { it.group }.size)
        val lint = api.list.items.filter { it.group == "lint" }.map { it.target }
        assertEquals(listOf(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "lint"), SourceFile(TestData.workspacePath(".moon/tasks/backend.yml"), "lint")), lint)
    }

    fun testGroupOfATaskFromTwoGlobalTasksOpensEachGlobalTask() {
        // The project worker renames format and test to check.
        val worker = insights("apps/worker/moon.yml").inheritedHints().first()
        assertEquals("3 inherited tasks", worker.text)
        assertEquals(3, worker.list.items.mapTo(HashSet()) { it.group }.size)
        val check = worker.list.items.filter { it.group == "check" }
        assertEquals(setOf("format", "test"), check.mapTo(HashSet()) { it.target?.taskId })
        assertEquals(setOf("format", "test"), check.mapTo(HashSet()) { it.secondary })
    }

    fun testListOfALocalTaskThatMergesWithTwoGlobalTasksHasGroups() {
        // The data gives worker a local task check. Then check merges with the global tasks format and test.
        val configs = loaded.tasks.taskConfigs.getValue("worker")
        val state = loaded.copy(tasks = loaded.tasks.copy(taskConfigs = loaded.tasks.taskConfigs + ("worker" to configs.copy(ownTasks = configs.ownTasks + "check"))))
        val insights = insightsOf("tasks:\n  check:\n    command: 'check'\n", "apps/worker/moon.yml", state)
        val list = insights.mergedHints().single().list
        assertEquals("Global Tasks That Merge with 'check'", list.title)
        assertEquals(setOf("format", "test"), list.items.mapTo(HashSet()) { it.group })
        assertNull("A click on the icon shows the list", directTarget(insights.gutterMarks().single()))
    }

    fun testClickOnTheIconOfALocalTaskWithOneGlobalTaskOpensTheGlobalTask() {
        val local = insights("apps/web/moon.yml").gutterMarks().single()
        assertEquals(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "lint"), directTarget(local))
        val global = insights(".moon/tasks/all.yml").gutterMarks().first { it.element.text == "lint" }
        assertNull("The icon of a global task shows the list", directTarget(global))
    }

    fun testProjectWithoutTheTasksKeyHasTheHintAtTheTop() {
        // The syntax comes from a file without the key tasks. The data comes from the project web.
        val insights = insightsOf("language: 'typescript'\n", "apps/web/moon.yml")
        val hint = insights.inheritedHints().single()
        assertEquals("3 inherited tasks", hint.text)
        assertEquals(0, hint.element.textOffset)
    }

    fun testGlobalTaskHasTheHintWithItsDependents() {
        val hint = insights(".moon/tasks/all.yml").dependentsHints().single()
        assertEquals("2 dependents", hint.text)
        assertEquals("lint", hint.element.text)
        assertEquals(listOf("ui:build", "ui:generate-icons"), hint.list.items.map { it.text })
        assertEquals(setOf("ui:lint"), hint.list.items.mapTo(HashSet()) { it.group })
        val internal = hint.list.items[1]
        assertEquals(SourceFile(TestData.workspacePath("packages/ui/moon.yml"), "generate-icons"), internal.target)
        assertEquals("packages/ui/moon.yml", internal.secondary)
    }

    fun testGroupOfARenamedTaskIsItsTarget() {
        val hint = insights(".moon/tasks/frontend.yml").dependentsHints().single { it.element.text == "typecheck" }
        assertEquals("1 dependent", hint.text)
        // The dependent web:build depends on the renamed task web:tsc. The item opens the task key build in the file of web.
        val item = hint.list.items.single()
        assertEquals("web:tsc", item.group)
        assertEquals("web:build", item.text)
        assertEquals("apps/web/moon.yml", item.secondary)
        assertEquals(SourceFile(TestData.workspacePath("apps/web/moon.yml"), "build"), item.target)
    }

    fun testDependentIsCountedOneTime() {
        val state = inheritingState(
            sources = mapOf("a" to "apps/web", "b" to "apps/api"),
            inherits = mapOf("a" to mapOf("lint" to "lint"), "b" to mapOf("lint" to "lint")),
            graph = mapOf("a:lint" to setOf("c:x"), "b:lint" to setOf("c:x", "c:y")),
        )
        val hint = insights(ALL, state).dependentsHints().single()
        assertEquals("2 dependents", hint.text)
        assertEquals(listOf("a:lint" to "c:x", "b:lint" to "c:x", "b:lint" to "c:y"), hint.list.items.map { it.group to it.text })
    }

    /** Gives the items of the dependents hint of `lint` in `all.yml`. The project `a` in [root] inherits `lint`, and its task `fmt` is `format` of `all.yml`. */
    private fun inheritedDependentItems(root: String): List<HintItem> {
        val state = inheritingState(mapOf("a" to root), mapOf("a" to mapOf("lint" to "lint", "fmt" to "format")), mapOf("a:lint" to setOf("a:fmt")))
        return insights(ALL, state).dependentsHints().single().list.items
    }

    fun testInheritedDependentOpensItsGlobalTaskKey() {
        // The project file apps/web/moon.yml has no key fmt. The item opens the key format in all.yml.
        val item = inheritedDependentItems("apps/web").single()
        assertEquals("a:fmt", item.text)
        assertEquals(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "format"), item.target)
        assertEquals(".moon/tasks/all.yml", item.secondary)
    }

    fun testInheritedDependentOfAProjectWithoutAFileOpensItsGlobalTaskKey() {
        val item = inheritedDependentItems("apps/missing").single()
        assertEquals(SourceFile(TestData.workspacePath(".moon/tasks/all.yml"), "format"), item.target)
    }

    /** Gives the dependents hints of `all.yml` with [text]. The project `a` inherits `lint` and `format`. Each task has one dependent. */
    private fun dependentsHintsOf(text: String): List<ConfigHint> {
        val state = inheritingState(
            sources = mapOf("a" to "apps/web"),
            inherits = mapOf("a" to mapOf("lint" to "lint", "format" to "format")),
            graph = mapOf("a:lint" to setOf("a:x"), "a:format" to setOf("a:y")),
        )
        return insightsOf(text, ALL, state).dependentsHints()
    }

    private fun joinedOnEditorLines(hints: List<ConfigHint>) = joinHintsOnOneLine(hints) { hintLine(myFixture.editor, it) }

    fun testJoinedHintUsesTheTaskIdWithoutQuotes() {
        val joined = joinedOnEditorLines(dependentsHintsOf("tasks: {'lint': {command: 'x'}, \"format\": {command: 'y'}}\n")).single()
        assertEquals("lint: 1 dependent; format: 1 dependent", joined.text)
    }

    fun testHintsOfTasksOnOneLineAreJoined() {
        val hints = dependentsHintsOf("tasks: {lint: {command: 'x'}, format: {command: 'y'}}\n")
        assertEquals(2, hints.size)
        val joined = joinedOnEditorLines(hints).single()
        assertEquals("lint: 1 dependent; format: 1 dependent", joined.text)
        assertEquals("lint", joined.element.text)
        assertEquals("Hints on This Line", joined.list.title)
        assertEquals(listOf("lint: a:lint" to "a:x", "format: a:format" to "a:y"), joined.list.items.map { it.group to it.text })
    }

    fun testHintsOnDifferentLinesAreNotJoined() {
        val hints = dependentsHintsOf("tasks:\n  lint: {command: 'x'}\n  format: {command: 'y'}\n")
        assertEquals(hints, joinedOnEditorLines(hints))
    }

    fun testJoinedHintWithAnEmptyListHasAnItemThatCannotBeSelected() {
        val hints = dependentsHintsOf("tasks: {lint: {command: 'x'}, format: {command: 'y'}}\n")
        val withEmpty = listOf(hints[0], ConfigHint(hints[1].element, hints[1].text, HintList("title", emptyList())))
        val items = joinedOnEditorLines(withEmpty).single().list.items
        assertEquals(HintItem("format: 1 dependent", null), items.last())
        assertEquals("lint: a:lint", items.first().group)
    }

    fun testNoHintAndOtherHintsStayIfTheTaskGraphFails() {
        service.taskGraph = { CliResult.Failed("moon cannot make the graph") }
        service.refresh()
        service.awaitState(TestData.loadedState(TestData.recordedTasks(), taskGraph = null))
        val insights = insights(".moon/tasks/all.yml")
        assertEmpty(insights.dependentsHints())
        assertEquals("Inherited by 9 projects", insights.inheritedHints().single().text)
        assertEquals(listOf("lint", "format"), insights.mergedHints().map { it.element.text })
        // The internal task keeps its merge strategies from the output of moon query projects.
        val format = insights.mergedHints().single { it.element.text == "format" }
        assertEquals("icons (args: replace)", format.list.items.single().text)
    }

    fun testProjectFileHasNoDependentsHint() {
        assertEmpty(insights("packages/ui/moon.yml").dependentsHints())
    }

    fun testItemOfARenamedTaskOpensTheLocalTaskKey() {
        // The project db renames the global task test to unit and has a local task unit.
        val hint = insights(".moon/tasks/backend.yml").mergedHints().single { it.element.text == "test" }
        assertEquals(SourceFile(TestData.workspacePath("packages/db/moon.yml"), "unit"), hint.list.items.single { it.text == "db" }.target)
    }

    fun testHintTextsHavePluralForms() {
        assertEquals("Merged in 1 project", MoonExplorerBundle.message("hint.mergedIn", 1))
        assertEquals("Merged in 0 projects", MoonExplorerBundle.message("hint.mergedIn", 0))
        assertEquals("Merged in 2 projects", MoonExplorerBundle.message("hint.mergedIn", 2))
        assertEquals("Inherited by 0 projects", MoonExplorerBundle.message("hint.inheritedBy", 0))
        assertEquals("Inherited by 1 project", MoonExplorerBundle.message("hint.inheritedBy", 1))
        assertEquals("1 inherited task", MoonExplorerBundle.message("hint.inheritedTasks", 1))
        assertEquals("2 inherited tasks", MoonExplorerBundle.message("hint.inheritedTasks", 2))
        assertEquals("0 inherited tasks", MoonExplorerBundle.message("hint.inheritedTasks", 0))
        assertEquals("0 dependents", MoonExplorerBundle.message("hint.dependents", 0))
        assertEquals("1 dependent", MoonExplorerBundle.message("hint.dependents", 1))
        assertEquals("2 dependents", MoonExplorerBundle.message("hint.dependents", 2))
    }

    fun testHintWithAnEmptyListHasNoClickAction() {
        val empty = codeVisionEntry("Inherited by 0 projects", HintList("title", emptyList()), MoonInheritedCodeVisionProvider.ID)
        assertFalse(empty is ClickableTextCodeVisionEntry)
        val one = codeVisionEntry("Inherited by 1 project", HintList("title", listOf(HintItem("web", null))), MoonInheritedCodeVisionProvider.ID)
        assertTrue(one is ClickableTextCodeVisionEntry)
    }

    fun testStrategiesAreOnlyInTheTextIfTheyExist() {
        assertEquals("web", withStrategies("web", emptyMap()))
        assertEquals("web (args: replace)", withStrategies("web", mapOf("args" to "replace")))
    }

    fun testOnlyAFileWithAConverterHasAConverter() {
        assertTrue(hasConfigConverter(myFixture.configureByText("moon.yml", "tasks: {}\n")))
        assertFalse(hasConfigConverter(myFixture.configureByText("notes.txt", "tasks: {}\n")))
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

    fun testDependentsProviderGivesTheHintsInTheEditor() {
        myFixture.openFileInEditor(TestData.workspaceVirtualFile(".moon/tasks/all.yml"))
        val entries = MoonDependentsCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file)
        assertEquals(listOf("2 dependents"), entries.map { (it.second as ClickableTextCodeVisionEntry).longPresentation })
        assertEquals(listOf("lint"), entries.map { myFixture.file.findElementAt(it.first.startOffset)?.text })
        assertEquals(MoonDependentsCodeVisionProvider.ID, entries.single().second.providerId)
    }

    fun testProvidersGiveTheHintsInTheProjectFile() {
        myFixture.configureFromExistingVirtualFile(TestData.workspaceVirtualFile("apps/web/moon.yml"))
        fun hints(provider: MoonCodeVisionProvider) = provider.computeForEditor(myFixture.editor, myFixture.file).map { (range, entry) ->
            (entry as ClickableTextCodeVisionEntry).longPresentation + " @ " + myFixture.editor.document.getText(range)
        }
        // The entries of workspace.inheritedTasks have no hints.
        assertEquals(listOf("3 inherited tasks @ tasks"), hints(MoonInheritedCodeVisionProvider()))
        assertEquals(listOf("Merged with all.yml @ lint"), hints(MoonMergedCodeVisionProvider()))
    }

    fun testLocalTaskThatMergesHasTheIconWithTheStrategiesInTheGutter() {
        myFixture.configureFromExistingVirtualFile(TestData.workspaceVirtualFile("apps/web/moon.yml"))
        val tooltips = myFixture.findAllGutters().mapNotNull { it.tooltipText }.filter { it.startsWith("Merged ") }
        assertEquals(listOf("Merged with all.yml (args: prepend, deps: replace)"), tooltips)
    }

    fun testTasksInFlowFormHaveHints() {
        val insights = insightsOf("workspace: {inheritedTasks: {exclude: [format]}}\ntasks: {lint: {}}\n", "apps/web/moon.yml")
        assertEquals(listOf("3 inherited tasks @ tasks"), insights.inheritedHints().map { it.text + " @ " + it.element.text })
        assertEquals(listOf("Merged with all.yml @ lint"), insights.mergedHints().map { it.text + " @ " + it.element.text })
    }

    fun testWorkspaceFilesHaveNoUnknownInheritedTasks() {
        assertEmpty(insights("apps/web/moon.yml").unknownInheritedTasks())
        assertEmpty(insights("tools/scripts/moon.yml").unknownInheritedTasks())
    }

    fun testEntriesWithUnknownGlobalTasks() {
        // The project scripts can inherit only format and lint from all.yml.
        val text = "workspace:\n  inheritedTasks:\n    include: [lint, lnt]\n    exclude: [format, test]\n    rename: {typecheck: tsc}\n"
        val unknown = insightsOf(text, "tools/scripts/moon.yml").unknownInheritedTasks()
        assertEquals(listOf("lnt", "test", "typecheck"), unknown.map { it.globalId })
        assertEquals(listOf("lnt", "test", "typecheck"), unknown.map { it.element.text })
    }

    fun testFileWithoutProjectDataHasNoUnknownInheritedTasks() {
        assertEmpty(insightsOf("workspace: {inheritedTasks: {include: [lnt]}}\n", ".moon/tasks/all.yml").unknownInheritedTasks())
    }

    fun testGlobalTaskFilesContainAnExcludedTask() {
        // The project web excludes format. Go to Declaration still opens the global task.
        val insights = insights("apps/web/moon.yml")
        assertEquals(listOf(SourceFile(TestData.workspacePath(ALL), "format")), insights.globalTaskFiles("format"))
        assertEquals(listOf(SourceFile(TestData.workspacePath(".moon/tasks/frontend.yml"), "typecheck")), insights.globalTaskFiles("typecheck"))
        assertEmpty(insights.globalTaskFiles("audit"))
        assertEquals(listOf("format", "lint", "test", "typecheck"), insights.globalTaskIds())
    }

    fun testCompletionIdsSkipTheOtherEntriesOfTheSameList() {
        // The project web can inherit format, lint, test, and typecheck.
        val insights = insightsOf("workspace:\n  inheritedTasks:\n    include: [lint]\n    exclude: [format, test]\n", "apps/web/moon.yml")
        val file = myFixture.file
        fun entry(text: String) = insights.inheritedTasksEntry(file.findElementAt(file.text.indexOf(text))!!)!!
        // An entry does not hide its own ID. Thus the user can complete the ID again.
        assertEquals(listOf("lint", "test", "typecheck"), insights.completionIds(entry("test]")))
        assertEquals(listOf("format", "lint", "typecheck"), insights.completionIds(entry("format,")))
        // The IDs of exclude do not apply to include.
        assertEquals(listOf("format", "lint", "test", "typecheck"), insights.completionIds(entry("lint]")))
    }

    fun testEntryOfAnElement() {
        val insights = insights("apps/web/moon.yml")
        val offset = myFixture.editor.document.text.indexOf("typecheck: 'tsc'")
        assertEquals("typecheck", insights.inheritedTasksEntry(myFixture.file.findElementAt(offset)!!)?.globalId)
        assertNull(insights.inheritedTasksEntry(myFixture.file.findElementAt(0)!!))
    }

    fun testExtensionsAreRegistered() {
        val providers = ExtensionPointName<DaemonBoundCodeVisionProvider>(DaemonBoundCodeVisionProvider.EP_NAME).extensionList
        assertTrue(providers.any { it is MoonInheritedCodeVisionProvider })
        assertTrue(providers.any { it is MoonMergedCodeVisionProvider })
        assertTrue(providers.any { it is MoonDependentsCodeVisionProvider })
        val settings = ExtensionPointName<CodeVisionGroupSettingProvider>("com.intellij.config.codeVisionGroupSettingProvider").extensionList
        assertEquals(MOON_CODE_VISION_GROUP, settings.first { it is MoonCodeVisionGroupSettingProvider }.groupId)
    }

    fun testNoTextUsesTheWordUsages() {
        val bundle = java.util.ResourceBundle.getBundle("messages.MoonExplorerBundle")
        val hintKeys = bundle.keySet().filter { it.startsWith("hint.") || it.startsWith("codeVision.") || it.startsWith("gutter.") }
        hintKeys.forEach { assertFalse(it, bundle.getString(it).contains("usage", ignoreCase = true)) }
    }

    fun testStepOpensTheTargetOfTheChosenItem() {
        val target = SourceFile(TestData.workspacePath("apps/web/moon.yml"), "lint")
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
        val job = service.scope.launch { openConfigFile(project, SourceFile(TestData.workspacePath("apps/web/moon.yml"), "lint")) }
        PlatformTestUtil.waitWithEventsDispatching("The file did not open", { job.isCompleted }, 10)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertEquals(text.indexOf("\n  lint:") + 3, editor.caretModel.offset)
    }

    private companion object {
        const val ALL = ".moon/tasks/all.yml"
    }
}
