package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
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

    private fun selectedProject(): String? = (panel.tree.selectionPath?.lastPathComponent as? ProjectNode)?.project?.id

    /** Updates the action and gives its presentation. If [withPanel] is false, the data context does not contain the panel. */
    private fun update(withPanel: Boolean = true): Presentation {
        val action = SelectOpenedFileAction()
        val e = if (withPanel) {
            tasksPanelEvent(action, project, panel)
        } else {
            TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        }
        action.update(e)
        return e.presentation
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
            listOf("MoonExplorer.RefreshTasks", "MoonExplorer.ShowRefreshFailure", "MoonExplorer.SelectOpenedFile", "MoonExplorer.GroupBy"),
            toolbar.childActionsOrStubs.map(actionManager::getId),
        )
    }

    fun testSelectsAndExpandsTheProjectOfTheFile() {
        panel.loadRecorded()
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertEquals("web", selectedProject())
        assertTrue(panel.tree.isExpanded(panel.tree.selectionPath))
        assertEmpty(messages)
    }

    fun testSelectsTheNestedProject() {
        panel.loadRecorded()
        panel.selectProjectOf(root.resolve("packages/ui/icons/moon.yml"))
        assertEquals("icons", selectedProject())
        panel.selectProjectOf(root.resolve("packages/ui/moon.yml"))
        assertEquals("ui", selectedProject())
    }

    fun testSelectsTheProjectBelowItsCategoryNode() {
        panel.changeCategory(TaskCategory.STACK)
        panel.loadRecorded()
        panel.selectProjectOf(root.resolve("apps/api/moon.yml"))
        assertEquals("api", selectedProject())
        val path: TreePath = panel.tree.selectionPath!!
        assertEquals("backend", (path.parentPath.lastPathComponent as CategoryNode).value)
        assertTrue(panel.tree.isExpanded(path.parentPath))
    }

    fun testSelectsTheProjectInItsFirstTagNode() {
        panel.changeCategory(TaskCategory.TAG)
        panel.loadRecorded()
        // web has the tags react and public. The tree shows web in the nodes public and react.
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertEquals("web", selectedProject())
        assertEquals("public", selectedTag())
        assertEquals(1, panel.tree.selectionCount)
    }

    /** Gives the tag node of the selected project node. */
    private fun selectedTag(): String? = (panel.tree.selectionPath?.parentPath?.lastPathComponent as? CategoryNode)?.value

    fun testSelectsTheProjectInTheTagNodeThatContainsTheSelection() {
        panel.changeCategory(TaskCategory.TAG)
        panel.loadRecorded()
        // The user can see web in the two tag nodes. The selection is in the node react.
        panel.tree.expandPath(panel.categoryPath("public"))
        panel.tree.selectionPath = panel.taskPathIn("react", "web:build")
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertEquals("web", selectedProject())
        assertEquals("react", selectedTag())
    }

    fun testSelectsTheProjectInATagNodeThatTheUserCanSee() {
        panel.changeCategory(TaskCategory.TAG)
        panel.loadRecorded()
        panel.tree.expandPath(panel.categoryPath("react"))
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertEquals("web", selectedProject())
        assertEquals("react", selectedTag())
        assertFalse(panel.tree.isExpanded(panel.categoryPath("public")))
    }

    fun testFileThatIsNotInAProjectShowsAMessage() {
        panel.loadRecorded()
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        panel.selectProjectOf(root.resolve("package.json"))
        assertEquals(listOf(MoonExplorerBundle.message("tasks.selectOpenedFile.notInProject", "package.json")), messages)
        assertEquals("The selection must not change", "web", selectedProject())
    }

    fun testFileInAProjectWithoutTasksShowsAMessage() {
        panel.loadRecorded()
        panel.selectProjectOf(root.resolve("packages/config/moon.yml"))
        assertEquals(listOf(MoonExplorerBundle.message("tasks.selectOpenedFile.noTasks", "config")), messages)
        assertNull(selectedProject())
    }

    fun testCannotSelectWhenTheMainGroupIsTaskName() {
        panel.loadRecorded()
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertFalse(panel.canSelectProject())
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertNull(panel.tree.selectionPath)
    }

    fun testActionIsEnabledWithAnOpenedFileAndTheProjectGroup() {
        panel.loadRecorded()
        assertFalse("No file is open", update().isEnabled)
        openInEditor("apps/web/moon.yml")
        assertTrue(update().isEnabled)
        panel.changeMainGroup(TaskGrouping.TASK_NAME)
        assertFalse("The tree has no project nodes", update().isEnabled)
        panel.changeMainGroup(TaskGrouping.PROJECT)
        panel.render(TasksState.Loading)
        assertFalse("The tree shows the old tasks during a load", update().isEnabled)
        panel.render(TasksState.NoWorkspace)
        assertFalse("The panel shows a message", update().isEnabled)
    }

    fun testCannotSelectDuringALoad() {
        panel.loadRecorded()
        panel.render(TasksState.Loading)
        assertFalse("The tree shows the old tasks", panel.canSelectProject())
        panel.selectProjectOf(root.resolve("apps/web/moon.yml"))
        assertNull(panel.tree.selectionPath)
        assertEmpty(messages)
        panel.loadRecorded()
        assertTrue(panel.canSelectProject())
    }

    fun testBalloonTextEscapesHtml() {
        val text = MoonExplorerBundle.message("tasks.selectOpenedFile.notInProject", "a<b>&c.ts")
        assertEquals("The file a&lt;b&gt;&amp;c.ts is not in a moon project.", plainTextHtml(text))
    }

    fun testActionIsDisabledForAFileThatIsNotOnTheDisk() {
        panel.loadRecorded()
        FileEditorManager.getInstance(project).openFile(LightVirtualFile("scratch.txt", "text"), true)
        assertFalse(update().isEnabled)
    }

    fun testActionIsHiddenWithoutThePanel() {
        assertFalse(update(withPanel = false).isVisible)
    }

    fun testActionSelectsTheProjectOfTheOpenedFile() {
        panel.loadRecorded()
        openInEditor("packages/ui/icons/moon.yml")
        val action = SelectOpenedFileAction()
        action.actionPerformed(tasksPanelEvent(action, project, panel))
        assertEquals("icons", selectedProject())
    }
}
