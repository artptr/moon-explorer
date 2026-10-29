package io.github.artptr.moonexplorer.model

import io.github.artptr.moonexplorer.testData.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class TaskSourcesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val state = TestData.recordedState()

    private fun task(target: String) = state.tasks.allTasks.single { it.target == target }

    /** Gives the files of [target] as paths from the workspace root. */
    private fun files(target: String) = taskSourceFiles(state, task(target)).map {
        assertEquals(targetTaskId(target), it.taskId)
        state.workspacePath(it.path)
    }

    /** Gives the targets of the key [taskId] in the file [path]. [path] is a path from the workspace root. */
    private fun keyTargets(path: String, taskId: String, loaded: TasksState.Loaded = state) =
        taskKeyTargets(loaded, loaded.root.resolve(path), taskId)

    @Test
    fun taskKeyInTheProjectConfigFileRunsTheTaskOfTheProject() {
        assertEquals(TaskKeyTargets.Single("web:build"), keyTargets("apps/web/moon.yml", "build"))
        assertEquals(TaskKeyTargets.Single("web:lint"), keyTargets("apps/web/moon.yml", "lint"))
        assertEquals(TaskKeyTargets.Single("icons:optimize"), keyTargets("packages/ui/icons/moon.yml", "optimize"))
    }

    @Test
    fun taskKeyInAnInheritedFileRunsTheTaskInAllProjectsThatInheritIt() {
        assertEquals(
            TaskKeyTargets.Shared(listOf("admin:test", "icons:test", "ui:test", "utils:test", "web:test")),
            keyTargets(".moon/tasks/frontend.yml", "test"),
        )
        assertEquals(TaskKeyTargets.Shared(listOf("api:test", "db:test", "worker:test")), keyTargets(".moon/tasks/backend.yml", "test"))
        val lint = keyTargets(".moon/tasks/all.yml", "lint") as TaskKeyTargets.Shared
        assertEquals(state.tasks.projects.map { "${it.id}:lint" }, lint.targets)
        // The layers of the project "config" contain lint, but moon gives no tasks for this project. Thus it has no target.
        assertFalse("config:lint" in lint.targets)
        assertEquals(9, lint.targets.size)
    }

    @Test
    fun taskKeyThatIsNotInTheLoadedDataHasNoTargets() {
        assertNull(keyTargets("apps/web/moon.yml", "missing"))
        assertNull(keyTargets(".moon/tasks/frontend.yml", "audit"))
        assertNull(keyTargets(".moon/tasks/all.yml", "missing"))
        assertNull(keyTargets("apps/web/package.json", "build"))
        assertNull(keyTargets(".moon/workspace.yml", "build"))
    }

    @Test
    fun twoSpellingsOfTheSameLayerGiveTheTargetsOfAllProjects() {
        // moon writes "./.moon/tasks/frontend.yml" for web and ".moon/tasks/frontend.yml" for the other projects.
        val configs = state.tasks.taskConfigs.mapValues { (id, configs) ->
            if (id != "web") configs else configs.copy(layers = configs.layers + ("test" to listOf("./.moon/tasks/frontend.yml")))
        }
        val loaded = TestData.loadedState(state.tasks.copy(taskConfigs = configs), state.root)
        assertEquals(
            TaskKeyTargets.Shared(listOf("admin:test", "icons:test", "ui:test", "utils:test", "web:test")),
            keyTargets(".moon/tasks/frontend.yml", "test", loaded),
        )
    }

    @Test
    fun inheritedTaskWithoutTheTaskInTheProjectHasNoTarget() {
        // The layers say that web inherits "test", but the loaded tasks of web do not contain it.
        val projects = state.tasks.projects.map { p -> if (p.id == "web") p.copy(tasks = p.tasks.filter { it.id != "test" }) else p }
        val loaded = state.copy(tasks = state.tasks.copy(projects = projects))
        val shared = keyTargets(".moon/tasks/frontend.yml", "test", loaded) as TaskKeyTargets.Shared
        assertFalse("web:test" in shared.targets)
        assertEquals(4, shared.targets.size)
    }

    @Test
    fun renamedTaskKeyInAnInheritedFileRunsTheTaskWithTheNewId() {
        // The project web renames the global task typecheck to tsc.
        assertEquals(
            TaskKeyTargets.Shared(listOf("admin:typecheck", "icons:typecheck", "ui:typecheck", "utils:typecheck", "web:tsc")),
            keyTargets(".moon/tasks/frontend.yml", "typecheck"),
        )
        assertNull(keyTargets(".moon/tasks/frontend.yml", "tsc"))
    }

    @Test
    fun excludedTaskKeyInAnInheritedFileDoesNotRunTheLocalTaskWithTheSameId() {
        // The project web excludes the global task format and has a local task format.
        val format = keyTargets(".moon/tasks/all.yml", "format") as TaskKeyTargets.Shared
        assertEquals(state.tasks.projects.map { "${it.id}:format" } - "scripts:format" - "web:format", format.targets)
        assertEquals(TaskKeyTargets.Single("web:format"), keyTargets("apps/web/moon.yml", "format"))
    }

    @Test
    fun taskKeyInAnInheritedFileDoesNotRunInAProjectThatDoesNotIncludeIt() {
        // The project scripts includes only the global task lint. Its inherited.layers also contain format.
        assertEquals(setOf("format", "lint"), state.tasks.taskConfigs.getValue("scripts").layers.keys)
        val format = keyTargets(".moon/tasks/all.yml", "format") as TaskKeyTargets.Shared
        assertFalse("scripts:format" in format.targets)
        assertTrue("scripts:lint" in (keyTargets(".moon/tasks/all.yml", "lint") as TaskKeyTargets.Shared).targets)
    }

    @Test
    fun renamedInheritedTaskIsInTheFileOfMoonTasksWithTheGlobalId() {
        val path = state.root.resolve(".moon/tasks/frontend.yml")
        assertEquals(listOf(SourceFile(path, "typecheck")), taskSourceFiles(state, task("web:tsc")))
    }

    @Test
    fun localTaskWithTheIdOfAnExcludedTaskIsOnlyInTheProjectConfigFile() {
        assertEquals(listOf("apps/web/moon.yml"), files("web:format"))
    }

    @Test
    fun inheritedLayersApplyIncludeExcludeAndRename() {
        val layers = mapOf("lint" to listOf("all.yml"), "test" to listOf("all.yml"), "build" to listOf("all.yml"), "e2e" to listOf("all.yml"))
        val taskIds = setOf("lint", "test", "build", "spec")
        val filter = InheritedTasksFilter(include = setOf("lint", "test", "e2e"), exclude = setOf("test"), rename = mapOf("e2e" to "spec"))
        assertEquals(
            mapOf("lint" to listOf(InheritedLayer("lint", "all.yml")), "spec" to listOf(InheritedLayer("e2e", "all.yml"))),
            TaskConfigs(layers = layers, filter = filter, taskIds = taskIds).inheritedLayers,
        )
        // An empty include list gives no inherited tasks.
        val none = TaskConfigs(layers = layers, filter = InheritedTasksFilter(include = emptySet()), taskIds = taskIds)
        assertEquals(emptyMap<String, List<InheritedLayer>>(), none.inheritedLayers)
    }

    @Test
    fun inheritedLayersContainOnlyTheTasksOfTheProject() {
        val layers = mapOf("lint" to listOf("all.yml"), "test" to listOf("all.yml"))
        assertEquals(setOf("lint"), TaskConfigs(layers = layers, taskIds = setOf("lint", "build")).inheritedLayers.keys)
        assertEquals(emptySet<String>(), TaskConfigs(layers = layers).inheritedLayers.keys)
    }

    @Test
    fun twoGlobalTasksWithTheSameIdAfterRenameGiveOneTaskWithTheFilesOfTheTwoTasks() {
        val layers = mapOf("lint" to listOf("all.yml"), "eslint" to listOf("frontend.yml"))
        val configs = TaskConfigs(layers = layers, filter = InheritedTasksFilter(rename = mapOf("eslint" to "lint")), taskIds = setOf("lint"))
        assertEquals(
            mapOf("lint" to listOf(InheritedLayer("lint", "all.yml"), InheritedLayer("eslint", "frontend.yml"))),
            configs.inheritedLayers,
        )
    }

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
            taskConfigs = mapOf(
                "web" to TaskConfigs(layers = mapOf("build" to listOf(".moon/tasks/missing.yml", ".moon/tasks/all.yml")), taskIds = setOf("build")),
            ),
        )
        val result = taskSourceFiles(TestData.loadedState(tasks, state.root), task("web:build"))
        assertEquals(listOf(state.root.resolve(".moon/tasks/all.yml")), result.map { it.path })
    }

    @Test
    fun taskWithoutConfigsHasNoFiles() {
        val result = taskSourceFiles(state.copy(tasks = state.tasks.copy(taskConfigs = emptyMap())), task("web:build"))
        assertEquals(emptyList<SourceFile>(), result)
    }

    @Test
    fun loadFindsTheConfigFilesOfTheRecordedWorkspace() {
        val files = state.configFiles
        assertEquals(state.tasks.projects.map { it.id }.toSet(), files.projects.keys)
        assertEquals(state.root.resolve("apps/web/moon.yml"), files.projects["web"])
        assertEquals(
            listOf("all.yml", "backend.yml", "frontend.yml").map { ".moon/tasks/$it" }.associateWith { state.root.resolve(it) },
            files.inherited,
        )
    }

    @Test
    fun fileOfOnlyTasksThatTheProjectDoesNotInheritIsNotFound() {
        val tasksDir = Files.createDirectories(temp.root.toPath().resolve(".moon/tasks"))
        Files.writeString(tasksDir.resolve("all.yml"), "")
        Files.writeString(tasksDir.resolve("other.yml"), "")
        val layers = mapOf("lint" to listOf(".moon/tasks/all.yml"), "format" to listOf(".moon/tasks/other.yml"))
        val configs = TaskConfigs(layers = layers, filter = InheritedTasksFilter(exclude = setOf("format")), taskIds = setOf("lint"))
        val tasks = WorkspaceTasks(projects = listOf(MoonProject("p", listOf(MoonTask("lint", "p:lint", null)))), taskConfigs = mapOf("p" to configs))
        val loaded = TestData.loadedState(tasks, temp.root.toPath())
        assertEquals(setOf(".moon/tasks/all.yml"), loaded.configFiles.inherited.keys)
    }

    @Test
    fun projectFileIsTheProjectConfigFileAtTheFirstLine() {
        assertEquals(SourceFile(state.root.resolve("apps/web/moon.yml"), null), projectSourceFile(state, "web"))
        assertEquals(SourceFile(state.root.resolve("packages/ui/icons/moon.yml"), null), projectSourceFile(state, "icons"))
    }

    @Test
    fun projectWithoutConfigFileHasNoFile() {
        Files.createDirectories(projectDir)
        val loaded = loadTempProject()
        assertNull(projectSourceFile(loaded, "p"))
        assertNull(projectSourceFile(loaded, "unknown"))
        assertEquals(emptyList<SourceFile>(), taskSourceFiles(loaded, MoonTask("build", "p:build", null)))
    }

    /** The directory of the project `p` in a temporary workspace. */
    private val projectDir: Path get() = temp.root.toPath().resolve("p")

    /** Makes the empty file [name] in [projectDir]. Gives the path of the file. */
    private fun writeProjectFile(name: String): Path {
        Files.createDirectories(projectDir)
        return Files.writeString(projectDir.resolve(name), "")
    }

    /** Loads the project `p` in [projectDir]. The configuration file of the project contains the task `build`. */
    private fun loadTempProject(): TasksState.Loaded {
        val tasks = WorkspaceTasks(
            projects = listOf(MoonProject("p", listOf(MoonTask("build", "p:build", null)))),
            sources = listOf(ProjectSource("p", "p")),
            taskConfigs = mapOf("p" to TaskConfigs(ownTasks = setOf("build"))),
        )
        return TestData.loadedState(tasks, temp.root.toPath())
    }

    @Test
    fun projectConfigFileCanHaveADifferentExtension() {
        writeProjectFile("moon.toml")
        assertEquals(projectDir.resolve("moon.toml"), projectSourceFile(loadTempProject(), "p")?.path)
        writeProjectFile("moon.yml")
        assertEquals(projectDir.resolve("moon.yml"), projectSourceFile(loadTempProject(), "p")?.path)
    }

    @Test
    fun filesAreTheFilesAtTheTimeOfTheLoad() {
        Files.createDirectories(projectDir)
        val loaded = loadTempProject()
        writeProjectFile("moon.yml")
        assertNull(projectSourceFile(loaded, "p"))
        assertEquals(emptyList<SourceFile>(), taskSourceFiles(loaded, MoonTask("build", "p:build", null)))
        assertEquals(SourceFile(projectDir.resolve("moon.yml"), null), projectSourceFile(loadTempProject(), "p"))
    }

    @Test
    fun taskInAFileThatIsNotYamlHasNoTaskId() {
        val file = writeProjectFile("moon.toml")
        assertEquals(listOf(SourceFile(file, null)), taskSourceFiles(loadTempProject(), MoonTask("build", "p:build", null)))
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
        val web = TestData.workspaceText("apps/web/moon.yml")
        assertEquals(web.indexOf("\n  build:") + 3, findTaskKeyOffset(web, "build"))
        assertEquals(web.indexOf("\n  lint:") + 3, findTaskKeyOffset(web, "lint"))
        val frontend = TestData.workspaceText(".moon/tasks/frontend.yml")
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
