package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import io.github.artptr.moonexplorer.model.MoonProject
import io.github.artptr.moonexplorer.model.MoonTask
import io.github.artptr.moonexplorer.model.ProjectSource
import io.github.artptr.moonexplorer.model.SourceFile
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.allTasks
import io.github.artptr.moonexplorer.model.workspacePath
import io.github.artptr.moonexplorer.testData.TestData
import java.nio.file.Files
import java.nio.file.Path

class JumpToSourceActionTest : BasePlatformTestCase() {

    private val state = TestData.recordedState()

    /** The lists that the fake chooser got. Each item contains the labels of the files. */
    private val chooserCalls = mutableListOf<List<String>>()

    private var chosen: ((SourceFile) -> Unit)? = null
    private var chooserFiles: List<SourceFile> = emptyList()

    private val action = JumpToSourceAction().apply {
        showChooser = { _, files, label, _, onChosen ->
            chooserCalls += files.map(label)
            chooserFiles = files
            chosen = onChosen
        }
    }

    override fun tearDown() {
        try {
            FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        } finally {
            super.tearDown()
        }
    }

    private fun task(target: String) = state.tasks.allTasks.single { it.target == target }

    private fun project(id: String) = state.tasks.projects.single { it.id == id }

    private fun event(task: MoonTask? = null, moonProject: MoonProject? = null, shown: TasksState.Loaded? = state) =
        TestActionEvent.createTestEvent(
            action,
            SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(SHOWN_TASKS, shown)
                .add(SELECTED_TASK, task)
                .add(SELECTED_PROJECT, moonProject)
                .build(),
        )

    private fun isAvailable(task: MoonTask? = null, moonProject: MoonProject? = null, shown: TasksState.Loaded? = state): Boolean =
        event(task, moonProject, shown).also(action::update).presentation.isEnabledAndVisible

    private fun perform(task: MoonTask? = null, moonProject: MoonProject? = null, shown: TasksState.Loaded = state) {
        action.actionPerformed(event(task, moonProject, shown))
        waitForAction()
    }

    private fun waitForAction() {
        val job = action.lastJob ?: return
        PlatformTestUtil.waitWithEventsDispatching("The action did not complete", { job.isCompleted }, 10)
    }

    /** Gives the path of the file in the editor from the workspace root, and the line of the caret. */
    private fun openedFileAndLine(): Pair<String, Int> {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: error("No file is open")
        return state.workspacePath(editor.virtualFile!!.toNioPath()) to editor.caretModel.logicalPosition.line
    }

    /** Gives the 0-based line of the first line of [path] that starts with [prefix]. */
    private fun lineOf(path: String, prefix: String): Int = TestData.workspaceText(path).lines().indexOfFirst { it.startsWith(prefix) }

    fun testAvailableForATaskAndAProject() {
        assertTrue(isAvailable(task("web:build")))
        assertTrue(isAvailable(task("web:test")))
        assertTrue(isAvailable(moonProject = project("web")))
    }

    fun testNotAvailableWithoutSelectionOrTree() {
        assertFalse(isAvailable())
        assertFalse(isAvailable(task("web:build"), shown = null))
    }

    fun testNotAvailableForAProjectWithoutConfigFile() {
        // The JVM deletes the directory when the tests complete.
        val root = FileUtil.createTempDirectory("moon-explorer-test", null, true).toPath()
        Files.createDirectories(root.resolve("apps/empty"))
        val empty = MoonProject("empty", listOf(MoonTask("build", "empty:build", null)))
        val loaded = TestData.loadedState(WorkspaceTasks(listOf(empty), listOf(ProjectSource("empty", "apps/empty"))), root)
        assertFalse(isAvailable(moonProject = empty, shown = loaded))
        assertFalse(isAvailable(empty.tasks.single(), shown = loaded))
    }

    /** The project of [loadTempProject]. */
    private val tempProject = MoonProject("a", listOf(MoonTask("build", "a:build", null)))

    /** Loads [tempProject] with the file `apps/a/moon.yml` in a temporary workspace. Gives the state and the path of the file. */
    private fun loadTempProject(): Pair<TasksState.Loaded, Path> {
        // The JVM deletes the directory when the tests complete.
        val root = FileUtil.createTempDirectory("moon-explorer-test", null, true).toPath()
        val file = Files.writeString(Files.createDirectories(root.resolve("apps/a")).resolve("moon.yml"), "tasks:\n")
        return TestData.loadedState(WorkspaceTasks(listOf(tempProject), listOf(ProjectSource("a", "apps/a"))), root) to file
    }

    fun testAvailabilityUsesTheFilesOfTheLoad() {
        val (loaded, file) = loadTempProject()
        // The plugin loads the tasks again after this change. Until then, the action uses the files of the last successful load.
        Files.delete(file)
        assertTrue(isAvailable(moonProject = tempProject, shown = loaded))
        perform(moonProject = tempProject, shown = loaded)
        assertNull("The file does not exist. Thus no file opens", FileEditorManager.getInstance(project).selectedTextEditor)
    }

    fun testPathThatIsNowADirectoryOpensNoFile() {
        val (loaded, file) = loadTempProject()
        Files.delete(file)
        Files.createDirectory(file)
        perform(moonProject = tempProject, shown = loaded)
        assertFalse("The action completes without an error", action.lastJob!!.isCancelled)
        assertNull("The path is a directory. Thus no file opens", FileEditorManager.getInstance(project).selectedTextEditor)
    }

    fun testOwnTaskOpensTheProjectConfigFileAtTheTaskLine() {
        perform(task("web:build"))
        assertEquals("apps/web/moon.yml" to lineOf("apps/web/moon.yml", "  build:"), openedFileAndLine())
        assertEmpty(chooserCalls)
    }

    fun testInheritedTaskOpensTheFileOfMoonTasksAtTheTaskLine() {
        perform(task("web:typecheck"))
        assertEquals(".moon/tasks/frontend.yml" to lineOf(".moon/tasks/frontend.yml", "  typecheck:"), openedFileAndLine())
    }

    fun testProjectOpensTheProjectConfigFileAtTheFirstLine() {
        perform(moonProject = project("web"))
        assertEquals("apps/web/moon.yml" to 0, openedFileAndLine())
    }

    fun testTaskKeyThatIsNotFoundOpensTheFirstLine() {
        // The state tells that the task is in the moon.yml of the project. The file does not contain the key.
        val missing = MoonTask("missing", "web:missing", null)
        val configs = state.tasks.taskConfigs.getValue("web")
        val tasks = state.tasks.copy(taskConfigs = state.tasks.taskConfigs + ("web" to configs.copy(ownTasks = configs.ownTasks + missing.id)))
        val loaded = state.copy(tasks = tasks)
        assertTrue(isAvailable(missing, shown = loaded))
        perform(missing, shown = loaded)
        assertEquals("apps/web/moon.yml" to 0, openedFileAndLine())
    }

    fun testTaskInTwoFilesShowsTheFilesWithTheProjectConfigFileFirst() {
        perform(task("web:lint"))
        assertEquals(listOf(listOf("apps/web/moon.yml", ".moon/tasks/all.yml")), chooserCalls)
        assertNull("No file opens before the user selects a file", FileEditorManager.getInstance(project).selectedTextEditor)

        chosen!!(chooserFiles[1])
        waitForAction()
        assertEquals(".moon/tasks/all.yml" to lineOf(".moon/tasks/all.yml", "  lint:"), openedFileAndLine())
    }

    fun testActionHasTheShortcutsOfJumpToSource() {
        // MoonToolWindowTest makes sure that the action is in the context menu.
        val keymap = KeymapManager.getInstance().activeKeymap
        val shortcuts = keymap.getShortcuts("MoonExplorer.JumpToSource").toList()
        assertNotEmpty(shortcuts)
        assertEquals(keymap.getShortcuts("EditSource").toList(), shortcuts)
    }
}
