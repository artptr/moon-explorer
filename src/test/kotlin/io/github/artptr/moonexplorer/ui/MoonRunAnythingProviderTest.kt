package io.github.artptr.moonexplorer.ui

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.model.MoonTasksService
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.allTasks
import io.github.artptr.moonexplorer.model.loadNoWorkspace
import io.github.artptr.moonexplorer.model.loadRecorded
import io.github.artptr.moonexplorer.model.loadStale
import io.github.artptr.moonexplorer.model.prepareForTests
import io.github.artptr.moonexplorer.model.startEndlessRefresh
import io.github.artptr.moonexplorer.testData.TestData
import kotlinx.coroutines.runBlocking

class MoonRunAnythingProviderTest : BasePlatformTestCase() {

    private lateinit var service: MoonTasksService
    private lateinit var provider: MoonRunAnythingProvider
    private val context by lazy { SimpleDataContext.getProjectContext(project) }

    override fun setUp() {
        super.setUp()
        service = project.service<MoonTasksService>()
        service.prepareForTests()
        provider = MoonRunAnythingProvider()
        service.loadRecorded()
    }

    override fun tearDown() {
        try {
            runBlocking { service.resetForTests() }
        } finally {
            super.tearDown()
        }
    }

    fun testTargetOfRunCommand() {
        assertEquals("web:build", MoonRunAnythingProvider.targetOf(listOf("run", "web:build")))
        assertEquals(":build", MoonRunAnythingProvider.targetOf(listOf("run", ":build")))
    }

    fun testNoTargetForOtherParameters() {
        assertNull(MoonRunAnythingProvider.targetOf(emptyList()))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("query", "projects")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "web:build", "web:lint")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "web:build", "--", "--watch")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "--help")))
        assertNull(MoonRunAnythingProvider.targetOf(listOf("run", "-h")))
    }

    fun testCompletionAfterMoonGivesRunAndTheLoadedTargets() {
        val expected = listOf("moon run") + TestData.recordedTasks().allTasks.map { "moon run ${it.target}" }
        assertSameElements(provider.getValues(context, "moon "), expected)
    }

    fun testCompletionBeforeTheSpaceAfterRunGivesTheLoadedTargets() {
        // "moo" is a part of the help command, and "moon run" has no space after "run".
        for (pattern in listOf("moon run", "moo")) {
            assertContainsElements(provider.getValues(context, pattern), "moon run", "moon run ui:storybook")
        }
    }

    fun testCompletionAfterMoonRunGivesTheLoadedTargets() {
        val values = provider.getValues(context, "moon run ")
        val expected = TestData.recordedTasks().allTasks.map { "moon run ${it.target}" }
        assertSameElements(values, expected)
    }

    fun testDuringARefreshTheCompletionUsesTheLastTargets() {
        // When you save a configuration file, the plugin starts a refresh. The completion must show the tasks during the refresh.
        assertEquals(TasksState.Loading(TestData.recordedState()), service.startEndlessRefresh())
        val expected = TestData.recordedTasks().allTasks.map { "moon run ${it.target}" }
        assertSameElements(provider.getValues(context, "moon run "), expected)
        assertEquals("moon query projects", provider.findMatchingValue(context, "moon query projects"))
    }

    fun testDuringTheFirstLoadTheProviderRunsCommandsButHasNoTargets() {
        runBlocking { service.resetForTests() }
        assertEquals(TasksState.Loading(null), service.state.value)
        assertSameElements(provider.getValues(context, "moon "), "moon run")
        assertEmpty(provider.getValues(context, "moon run "))
        assertEquals("moon query projects", provider.findMatchingValue(context, "moon query projects"))
    }

    fun testWithoutAWorkspaceTheProviderDoesNotCompleteOrRunCommands() {
        service.loadNoWorkspace()
        for (pattern in listOf("moo", "moon ", "moon run ")) {
            assertEmpty(pattern, provider.getValues(context, pattern))
        }
        assertNull(provider.findMatchingValue(context, "moon --version"))
        assertNull(provider.findMatchingValue(context, "moon run web:build"))
    }

    fun testCompletionUsesTheLastTargetsAfterAFailedRefresh() {
        service.loadStale()
        assertContainsElements(provider.getValues(context, "moon run "), "moon run ui:storybook")
    }

    fun testNoCompletionAfterATarget() {
        assertEmpty(provider.getValues(context, "moon run web:build "))
    }

    fun testProviderMatchesAllMoonCommands() {
        for (command in listOf("moon", "moon run", "moon run web:build", "moon query projects", "moon run --help", "moon run web:build web:lint")) {
            assertEquals(command, provider.findMatchingValue(context, command))
        }
    }

    fun testCommandErrorsHaveTheirOwnNotificationGroup() {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(MoonRunAnythingProvider.COMMAND_GROUP_ID)
        assertEquals(NotificationDisplayType.BALLOON, group.displayType)
    }

    fun testProviderDoesNotMatchOtherCommands() {
        assertNull(provider.findMatchingValue(context, "npm run build"))
        assertNull(provider.findMatchingValue(context, "moonx run web:build"))
        assertNull(provider.findMatchingValue(context, "echo moon"))
    }

    fun testProviderIsTheFirstProviderForMoonCommands() {
        for (command in listOf("moon run web:build", "moon query projects")) {
            val first = RunAnythingProvider.EP_NAME.extensionList.first {
                @Suppress("UNCHECKED_CAST")
                (it as RunAnythingProvider<Any?>).findMatchingValue(context, command) != null
            }
            assertInstanceOf(first, MoonRunAnythingProvider::class.java)
        }
    }

    fun testRunRunsTheTargetWithARunConfiguration() {
        val tasks = mutableListOf<Pair<Project, String>>()
        val commands = mutableListOf<List<String>>()
        provider.runTask = { p, target -> tasks.add(p to target) }
        provider.runCommand = { _, args -> commands.add(args) }
        provider.execute(context, "moon run web:build")
        assertEquals(listOf(project to "web:build"), tasks)
        assertEmpty(commands)
    }

    fun testRunRunsOtherCommandsWithTheCommandRunner() {
        val tasks = mutableListOf<String>()
        val commands = mutableListOf<Pair<Project, List<String>>>()
        provider.runTask = { _, target -> tasks.add(target) }
        provider.runCommand = { p, args -> commands.add(p to args) }
        provider.execute(context, "moon query projects")
        provider.execute(context, "moon run --help")
        provider.execute(context, "moon run web:build web:lint")
        provider.execute(context, "moon")
        assertEmpty(tasks)
        assertEquals(
            listOf(
                project to listOf("query", "projects"),
                project to listOf("run", "--help"),
                project to listOf("run", "web:build", "web:lint"),
                project to emptyList(),
            ),
            commands,
        )
    }

    fun testProviderIsRegistered() {
        assertTrue(RunAnythingProvider.EP_NAME.extensionList.any { it is MoonRunAnythingProvider })
    }
}
