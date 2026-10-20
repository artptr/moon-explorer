package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.model.TaskCategory
import io.github.artptr.moonexplorer.model.TaskGrouping
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.settings.MoonSettings
import io.github.artptr.moonexplorer.settings.resetForTests
import io.github.artptr.moonexplorer.testData.TestData
import javax.swing.tree.TreePath

class SelectOpenedFileActionTest : BasePlatformTestCase() {

    private lateinit var panel: MoonTasksPanel

    private val messages = mutableListOf<String>()

    private val root = TestData.workspaceRoot

    override fun setUp() {
        super.setUp()
        project.service<MoonSettings>().resetForTests()
        panel = MoonTasksPanel(project)
        Disposer.register(testRootDisposable, panel)
        panel.showInfo = { messages += it }
        // The test workspace is not in the light project. Let the tests open its files.
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
    }

    override fun tearDown() {
        try {
            FileEditorManager.getInstance(project).let { manager -> manager.openFiles.forEach(manager::closeFile) }
            project.service<MoonSettings>().resetForTests()
        } finally {
            super.tearDown()
        }
    }

    private fun loadRecorded() = panel.render(TasksState.Loaded(TestData.recordedTasks(), root))

    private fun selectedProject(): String? = (panel.tree.selectionPath?.lastPathComponent as? ProjectNode)?.project?.id

    private fun event(action: SelectOpenedFileAction) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(TASKS_PANEL, panel).build(),
    )

    private fun update(): Boolean {
        val action = SelectOpenedFileAction()
        val e = event(action)
        action.update(e)
        return e.presentation.isEnabled
    }

    private fun openInEditor(file: String) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve(file))
            ?: error("The test workspace does not contain $file")
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    fun testToolbarHasTheAction() {
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.getAction(MoonTasksPanel.TOOLBAR_GROUP_ID) as DefaultActionGroup
        assertEquals(
            listOf("MoonExplorer.RefreshTasks", "MoonExplorer.SelectOpenedFile", "MoonExplorer.GroupBy"),
            toolbar.childActionsOrStubs.map(actionManager::getId),
        )
    }

    fun testSelectsAndExpandsTheProjectOfTheFile() {
        loadRecorded()
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertEquals("web", selectedProject())
        assertTrue(panel.tree.isExpanded(panel.tree.selectionPath))
        assertEmpty(messages)
    }

    fun testSelectsTheNestedProject() {
        loadRecorded()
        panel.selectProjectOf(root.resolve("packages/ui/icons/moon.yml"))
        assertEquals("icons", selectedProject())
        panel.selectProjectOf(root.resolve("packages/ui/moon.yml"))
        assertEquals("ui", selectedProject())
    }

    fun testSelectsTheProjectBelowItsCategoryNode() {
        panel.changeCategory(TaskCategory.STACK)
        loadRecorded()
        panel.selectProjectOf(root.resolve("apps/api/moon.yml"))
        assertEquals("api", selectedProject())
        val path: TreePath = panel.tree.selectionPath!!
        assertEquals("backend", (path.parentPath.lastPathComponent as CategoryNode).value)
        assertTrue(panel.tree.isExpanded(path.parentPath))
    }

    fun testFileThatIsNotInAProjectShowsAMessage() {
        loadRecorded()
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        panel.selectProjectOf(root.resolve("package.json"))
        assertEquals(listOf(MoonExplorerBundle.message("tasks.selectOpenedFile.notInProject", "package.json")), messages)
        assertEquals("The selection must not change", "web", selectedProject())
    }

    fun testFileInAProjectWithoutTasksShowsAMessage() {
        loadRecorded()
        panel.selectProjectOf(root.resolve("packages/config/moon.yml"))
        assertEquals(listOf(MoonExplorerBundle.message("tasks.selectOpenedFile.noTasks", "config")), messages)
        assertNull(selectedProject())
    }

    fun testCannotSelectWhenTheMainGroupIsTaskName() {
        loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertFalse(panel.canSelectProject())
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertNull(panel.tree.selectionPath)
    }

    fun testActionIsEnabledWithAnOpenedFileAndTheProjectGroup() {
        loadRecorded()
        assertFalse("No file is open", update())
        openInEditor("apps/web/moon.yml")
        assertTrue(update())
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertFalse("The tree has no project nodes", update())
        panel.changeMainGroup(TaskGrouping.PROJECT)
        panel.render(TasksState.NoWorkspace)
        assertFalse("The panel shows a message", update())
    }

    fun testCannotSelectDuringALoad() {
        loadRecorded()
        panel.render(TasksState.Loading)
        assertFalse("The tree shows the old tasks", panel.canSelectProject())
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertNull(panel.tree.selectionPath)
        assertEmpty(messages)
        loadRecorded()
        assertTrue(panel.canSelectProject())
    }

    fun testActionIsDisabledDuringALoad() {
        loadRecorded()
        openInEditor("apps/web/moon.yml")
        panel.render(TasksState.Loading)
        assertFalse(update())
    }

    fun testBalloonTextEscapesHtml() {
        val text = MoonExplorerBundle.message("tasks.selectOpenedFile.notInProject", "a<b>&c.ts")
        assertEquals("The file a&lt;b&gt;&amp;c.ts is not in a moon project.", balloonHtml(text))
    }

    fun testActionIsDisabledForAFileThatIsNotOnTheDisk() {
        loadRecorded()
        FileEditorManager.getInstance(project).openFile(LightVirtualFile("scratch.txt", "text"), true)
        assertFalse(update())
    }

    fun testActionIsHiddenWithoutThePanel() {
        val action = SelectOpenedFileAction()
        val e = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(e)
        assertFalse(e.presentation.isVisible)
    }

    fun testActionSelectsTheProjectOfTheOpenedFile() {
        loadRecorded()
        openInEditor("packages/ui/icons/moon.yml")
        val action = SelectOpenedFileAction()
        action.actionPerformed(event(action))
        assertEquals("icons", selectedProject())
    }
}
