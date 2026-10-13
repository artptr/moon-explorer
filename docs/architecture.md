# Architecture

## 1. Platform

| Item | Value |
|------|-------|
| Plugin ID | `io.github.artptr.moonexplorer` |
| Language | Kotlin |
| Build tool | Gradle, with the IntelliJ Platform Gradle Plugin 2.x |
| Target platform | IntelliJ Platform 2025.3 and later |
| JDK | 21 |
| Platform dependency | `com.intellij.modules.platform` (all IntelliJ-based IDEs) |

## 2. Current structure

The plugin has three layers. Each layer uses only the layer below it.

1. **UI layer** (`ui`): tool windows and actions.
2. **Model layer** (`model`): data classes for the workspace, projects, and tasks. A service of the IDE project keeps the current model.
3. **CLI layer** (`cli`): finds the workspace root, starts the `moon` executable, and gives its output.

The model layer does not use Swing. The CLI layer does not use the IntelliJ PSI.
The parser of the moon JSON output is in the model layer. Thus the CLI layer does not use the model classes.

| Component | Class | Registration |
|-----------|-------|--------------|
| Tool window **Moon** | `ui.MoonToolWindowFactory`, `ui.MoonTasksPanel` | `plugin.xml` |
| Tree nodes and renderer | `ui.TaskTreeModel.kt` | None |
| Action **Refresh** in the group `MoonExplorer.TasksToolbar` | `ui.RefreshTasksAction` | `plugin.xml` |
| State of the tasks for the IDE project | `model.MoonTasksService` | Light service |
| Load of the tasks when the IDE project opens | `model.MoonTasksStartupActivity` | `plugin.xml` |
| Load of the tasks and the result state | `model.TasksLoader`, `model.TasksState` | None |
| Parser of `moon query tasks` | `model.QueryTasksParser` | None |
| Find the workspace root | `cli.MoonWorkspaceLocator` | None |
| Start of the `moon` executable | `cli.MoonCli` | None |
| Resource bundle | `MoonExplorerBundle` | `plugin.xml` |

**NOTE:** Record each change to the layers in an ADR in [adr/](adr/).

## 3. Data flow

1. The plugin finds the workspace root. It starts at the IDE project directory and goes up through the parent directories. The workspace root has a `.moon/` directory with a `workspace.*` file.
2. The plugin runs `moon query tasks` in the workspace root. In moon v2, the output of this command is always JSON.
3. The plugin reads the JSON with Gson. The IntelliJ Platform contains Gson. Thus the plugin has no new dependency.
4. The service sets the new state. The tool window shows the tree or a message.

The plugin uses `moon` from `PATH`.

## 4. Threading

All calls to the moon CLI run on a background thread in a coroutine.
The UI layer updates Swing components on the EDT only.
Refer to the skill `intellij-plugin-dev` for the full rules.
