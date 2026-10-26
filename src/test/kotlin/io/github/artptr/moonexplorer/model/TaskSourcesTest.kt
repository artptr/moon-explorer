package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class TaskSourcesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val state = TestData.recordedState()

    private fun task(target: String) = state.tasks.allTasks.single { it.target == target }

    /** Gives the files of [target] as paths from the workspace root. */
    private fun files(target: String) = taskSourceFiles(state, task(target)).map {
        assertEquals(targetTaskId(target), it.taskId)
        state.root.relativize(it.path).toString().replace(File.separatorChar, '/')
    }

    private fun workspaceText(path: String) = File(TestData.workspace, path).readText()

    @Test
    fun taskOfTheProjectIsInTheProjectConfigFile() {
        assertEquals(listOf("apps/web/moon.yml"), files("web:build"))
    }

    @Test
    fun inheritedTaskIsInTheFileOfMoonTasks() {
        assertEquals(listOf(".moon/tasks/frontend.yml"), files("web:test"))
        assertEquals(listOf(".moon/tasks/backend.yml"), files("api:audit"))
    }

    @Test
    fun changedInheritedTaskIsInTwoFilesWithTheProjectConfigFileFirst() {
        assertEquals(listOf("apps/web/moon.yml", ".moon/tasks/all.yml"), files("web:lint"))
    }

    @Test
    fun layerFileThatDoesNotExistIsNotInTheList() {
        val tasks = state.tasks.copy(
            taskConfigs = mapOf("web" to TaskConfigs(layers = mapOf("build" to listOf(".moon/tasks/missing.yml", ".moon/tasks/all.yml")))),
        )
        val result = taskSourceFiles(TasksState.Loaded(tasks, state.root), task("web:build"))
        assertEquals(listOf(state.root.resolve(".moon/tasks/all.yml")), result.map { it.path })
    }

    @Test
    fun taskWithoutConfigsHasNoFiles() {
        val result = taskSourceFiles(TasksState.Loaded(state.tasks.copy(taskConfigs = emptyMap()), state.root), task("web:build"))
        assertEquals(emptyList<SourceFile>(), result)
    }

    @Test
    fun projectFileIsTheProjectConfigFileAtTheFirstLine() {
        assertEquals(SourceFile(state.root.resolve("apps/web/moon.yml"), null), projectSourceFile(state, "web"))
        assertEquals(SourceFile(state.root.resolve("packages/ui/icons/moon.yml"), null), projectSourceFile(state, "icons"))
    }

    @Test
    fun projectWithoutConfigFileHasNoFile() {
        val root = temp.root.toPath()
        File(temp.root, "apps/empty").mkdirs()
        val tasks = WorkspaceTasks(
            projects = emptyList(),
            sources = listOf(ProjectSource("empty", "apps/empty")),
            taskConfigs = mapOf("empty" to TaskConfigs(ownTasks = setOf("build"))),
        )
        val loaded = TasksState.Loaded(tasks, root)
        assertNull(projectSourceFile(loaded, "empty"))
        assertNull(projectSourceFile(loaded, "unknown"))
        assertEquals(emptyList<SourceFile>(), taskSourceFiles(loaded, MoonTask("build", "empty:build", null)))
    }

    @Test
    fun projectConfigFileCanHaveADifferentExtension() {
        val dir = temp.newFolder("project").toPath()
        assertNull(findProjectConfigFile(dir))
        File(dir.toFile(), "moon.toml").writeText("")
        assertEquals(dir.resolve("moon.toml"), findProjectConfigFile(dir))
        File(dir.toFile(), "moon.yml").writeText("")
        assertEquals(dir.resolve("moon.yml"), findProjectConfigFile(dir))
    }

    @Test
    fun onlyYamlFilesAreYamlFiles() {
        assertTrue(isYamlFile(Path.of("moon.yml")))
        assertTrue(isYamlFile(Path.of(".moon/tasks/all.YAML")))
        assertFalse(isYamlFile(Path.of("moon.json")))
        assertFalse(isYamlFile(Path.of("yml")))
    }

    @Test
    fun keyOffsetInTheRecordedWorkspace() {
        val web = workspaceText("apps/web/moon.yml")
        assertEquals(web.indexOf("\n  build:") + 3, findTaskKeyOffset(web, "build"))
        assertEquals(web.indexOf("\n  lint:") + 3, findTaskKeyOffset(web, "lint"))
        val frontend = workspaceText(".moon/tasks/frontend.yml")
        assertEquals(frontend.indexOf("\n  test:") + 3, findTaskKeyOffset(frontend, "test"))
    }

    @Test
    fun keyOffsetIsTheStartOfTheKey() {
        val text = "language: 'ts'\ntasks:\n    build:\n      command: 'x'\n    test:\n      command: 'y'\n"
        assertEquals(text.indexOf("test:"), findTaskKeyOffset(text, "test"))
    }

    @Test
    fun quotedKeysAreFound() {
        val text = "tasks:\n  'build':\n    command: a\n  \"test\" :\n    command: b\n  'it''s':\n    command: c\n"
        assertEquals(text.indexOf("'build'"), findTaskKeyOffset(text, "build"))
        assertEquals(text.indexOf("\"test\""), findTaskKeyOffset(text, "test"))
        assertEquals(text.indexOf("'it''s'"), findTaskKeyOffset(text, "it's"))
    }

    @Test
    fun nestedKeysWithTheSameNameAreIgnored() {
        val text = "tasks:\n  dev:\n    options:\n      build: true\n  build:\n    command: a\n"
        assertEquals(text.indexOf("  build:\n    command") + 2, findTaskKeyOffset(text, "build"))
        assertNull(findTaskKeyOffset(text, "options"))
    }

    @Test
    fun keysOutsideTheTasksKeyAreIgnored() {
        val text = "build:\n  command: a\ntasks:\n  test:\n    command: b\nother:\n  build:\n    command: c\n"
        assertNull(findTaskKeyOffset(text, "build"))
        assertEquals(text.indexOf("test:"), findTaskKeyOffset(text, "test"))
    }

    @Test
    fun nestedTasksKeyIsNotTheTopLevelKey() {
        val text = "workspace:\n  tasks:\n    build:\n      command: a\n"
        assertNull(findTaskKeyOffset(text, "build"))
    }

    @Test
    fun commentsBlankLinesAndCrLfAreIgnored() {
        val text = "# Tasks\r\ntasks: # all tasks\r\n\r\n# A comment at column 0\r\n  # build: in a comment\r\n  lint:\r\n    command: a\r\n  build:\r\n"
        assertEquals(text.indexOf("  build:\r\n") + 2, findTaskKeyOffset(text, "build"))
    }

    @Test
    fun keyIsNotFoundInOtherText() {
        assertNull(findTaskKeyOffset("", "build"))
        assertNull(findTaskKeyOffset("tasks:", "build"))
        assertNull(findTaskKeyOffset("tasks: {}\nbuild:\n", "build"))
        assertNull(findTaskKeyOffset("tasks:\n  build-all:\n  prebuild:\n", "build"))
    }
}
