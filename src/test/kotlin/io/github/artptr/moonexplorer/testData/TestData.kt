package io.github.artptr.moonexplorer.testData

import io.github.artptr.moonexplorer.model.QueryProjectsParser
import io.github.artptr.moonexplorer.model.TasksState
import io.github.artptr.moonexplorer.model.WorkspaceTasks
import io.github.artptr.moonexplorer.model.findExistingConfigFiles
import java.io.File
import java.nio.file.Path

/** Paths to the test data in `src/test/testData/`. Refer to `docs/development.md`. */
object TestData {
    /** Gradle sets the property `moonExplorer.testData`. Without it, the path is relative to the repository root. */
    private val root = File(System.getProperty("moonExplorer.testData") ?: "src/test/testData")

    /** The test workspace with sample data. */
    val workspace = File(root, "workspace")

    /** The absolute path of [workspace]. The plugin gives this path as the workspace root. */
    val workspaceRoot: Path get() = workspace.toPath().toAbsolutePath().normalize()

    /** Reads the file [path] from [workspace]. [path] is a path from the workspace root, for example `apps/web/moon.yml`. */
    fun workspaceText(path: String): String = File(workspace, path).readText()

    /** The recorded moon CLI output for the test workspace. */
    val moon = File(root, "moon")

    /** Reads a recorded file from [moon]. */
    fun recorded(name: String): String = File(moon, name).readText()

    /** The projects and tasks of the recorded `moon query projects` output. */
    fun recordedTasks(): WorkspaceTasks = QueryProjectsParser.parse(recorded("query-projects.json"))

    /** The state after a successful load of the recorded `moon query projects` output in [workspace]. */
    fun recordedState(): TasksState.Loaded = loadedState(recordedTasks())

    /** The state after a successful load of [tasks] in [root]. As after a real load, the state contains the configuration files on the disk. */
    fun loadedState(tasks: WorkspaceTasks, root: Path = workspaceRoot): TasksState.Loaded =
        TasksState.Loaded(tasks, root, findExistingConfigFiles(tasks, root))
}
