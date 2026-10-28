package io.github.artptr.moonexplorer.yaml

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.testData.TestData
import io.github.artptr.moonexplorer.ui.RunInEachProjectGroup
import io.github.artptr.moonexplorer.ui.RunTargetsAction
import kotlinx.coroutines.runBlocking
import org.jetbrains.yaml.YAMLLanguage
import java.nio.file.Files

class MoonTaskRunLineMarkerContributorTest : BasePlatformTestCase() {

    private val contributor = MoonTaskRunLineMarkerContributor()

    private val service get() = project.service<MoonTasksService>()

    override fun setUp() {
        super.setUp()
        service.prepareForTests()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    /** Gives the PSI of the file [path] in the test workspace. [path] is a path from the workspace root. */
    private fun workspaceFile(path: String) = project.psiFileAt(TestData.workspaceRoot.resolve(path))

    /** Gives the text of each leaf with an icon and the icon information, in the sequence of the file. */
    private fun markers(file: PsiFile): List<Pair<String, RunLineMarkerContributor.Info>> =
        file.leaves().mapNotNull { leaf -> contributor.getInfo(leaf)?.let { leaf.text to it } }

    private fun tooltip(info: RunLineMarkerContributor.Info, element: PsiElement): String? = info.tooltipProvider?.apply(element)

    /** Gives the actions of the popup. The popup shows the actions of [RunInEachProjectGroup] without a submenu. */
    private fun popupActions(info: RunLineMarkerContributor.Info): List<AnAction> =
        info.actions.flatMap { if (it is RunInEachProjectGroup) it.getChildren(null).toList() else listOf(it) }

    /** Writes [text] to a `moon.yml` file in a temporary directory and gives the task keys of the file. */
    private fun taskKeysIn(text: String): List<TaskKey> {
        val path = FileUtil.createTempDirectory("moon-keys", null, true).toPath().resolve("moon.yml")
        Files.writeString(path, text)
        return project.psiFileAt(path).leaves().mapNotNull(::taskKeyAt)
    }

    fun testContributorIsRegisteredForYaml() {
        val contributors = LanguageExtension<RunLineMarkerContributor>("com.intellij.runLineMarkerContributor").allForLanguage(YAMLLanguage.INSTANCE)
        assertTrue(contributors.any { it is MoonTaskRunLineMarkerContributor })
    }

    fun testEachTaskKeyOfAProjectHasTheRunIcon() {
        service.loadRecorded()
        val file = workspaceFile("apps/web/moon.yml")
        val markers = markers(file)
        assertEquals(listOf("dev", "build", "preview", "lint", "format"), markers.map { it.first })
        markers.forEach { (_, info) ->
            assertSame(AllIcons.RunConfigurations.TestState.Run, info.icon)
            // The actions of the executors use MoonTaskRunConfigurationProducer.
            assertTrue(info.actions.isNotEmpty())
            assertTrue(info.actions.none { it is RunTargetsAction })
        }
        assertEquals("Run 'web:build'", tooltip(markers[1].second, file))
    }

    fun testSharedTaskKeyHasTheIconForManyProjectsAndAPopup() {
        service.loadRecorded()
        val file = workspaceFile(".moon/tasks/frontend.yml")
        val markers = markers(file)
        assertEquals(listOf("test", "typecheck"), markers.map { it.first })
        val info = markers.first().second
        assertSame(AllIcons.RunConfigurations.TestState.Run_run, info.icon)
        assertEquals("Inherited by 5 projects", tooltip(info, file))

        val targets = listOf("admin:test", "icons:test", "ui:test", "utils:test", "web:test")
        assertTrue(info.actions[2] is Separator)
        val actions = popupActions(info)
        val runs = actions.filterIsInstance<RunTargetsAction>()
        assertEquals(
            listOf("Run in 5 Projects", "Run Affected in 5 Projects") + targets.map { "Run '$it'" },
            runs.map { it.templatePresentation.text },
        )
        assertEquals(listOf(targets, targets) + targets.map(::listOf), runs.map { it.targets })
        assertEquals(listOf(false, true) + targets.map { false }, runs.map { it.affected })
    }

    fun testSharedTaskActionsRunTheFullListOfTargets() {
        service.loadRecorded()
        val info = markers(workspaceFile(".moon/tasks/backend.yml")).first { it.first == "test" }.second
        val calls = mutableListOf<Pair<List<String>, Boolean>>()
        val event = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)
        info.actions.filterIsInstance<RunTargetsAction>().take(2).forEach { action ->
            action.run = { _, targets, affected -> calls += targets to affected }
            action.actionPerformed(TestActionEvent.createTestEvent(action, event))
        }
        val targets = listOf("api:test", "db:test", "worker:test")
        assertEquals(listOf(targets to false, targets to true), calls)
    }

    fun testOtherKeysAndFilesHaveNoIcons() {
        service.loadRecorded()
        assertEquals(emptyList<String>(), markers(workspaceFile(".moon/workspace.yml")).map { it.first })
        // The keys of the tasks in the configuration of a task, for example "command" and "deps", have no icons.
        val keys = markers(workspaceFile("apps/admin/moon.yml")).map { it.first }
        assertEquals(listOf("build", "dev"), keys.sorted())
    }

    fun testNoIconsBeforeTheFirstLoad() {
        assertEquals(emptyList<String>(), markers(workspaceFile("apps/web/moon.yml")).map { it.first })
        assertEquals(emptyList<String>(), markers(workspaceFile(".moon/tasks/all.yml")).map { it.first })
    }

    fun testQuotedTaskKeyGivesTheKeyTextWithoutQuotes() {
        val keys = taskKeysIn("tasks:\n  'build':\n    command: a\n  \"lint\":\n    command: b\n  test:\n    deps: ['build']\n")
        assertEquals(listOf("build", "lint", "test"), keys.map { it.taskId })
        assertEquals(1, keys.map { it.file }.distinct().size)
    }

    fun testNestedTasksKeyIsNotTheTopLevelKey() {
        assertEquals(emptyList<TaskKey>(), taskKeysIn("workspace:\n  tasks:\n    build:\n      command: a\n"))
    }
}
