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
The `settings` package contains the settings state and the settings page.
The parser of the moon JSON output is in the model layer. Thus the CLI layer does not use the model classes.

| Component | Class | Registration |
|-----------|-------|--------------|
| Tool window **Moon** | `ui.MoonToolWindowFactory`, `ui.MoonTasksPanel` | `plugin.xml` |
| Tree nodes and renderer | `ui.TaskTreeModel.kt` | None |
| Action **Refresh** in the group `MoonExplorer.TasksToolbar` | `ui.RefreshTasksAction` | `plugin.xml` |
| State of the tasks for the IDE project | `model.MoonTasksService` | None (light service) |
| Load of the tasks when the IDE project opens | `model.MoonTasksStartupActivity` | `plugin.xml` |
| Load of the tasks and the result state | `model.TasksLoader`, `model.TasksState` | None |
| Parser of `moon query tasks` | `model.QueryTasksParser` | None |
| Find the workspace root | `cli.MoonWorkspaceLocator` | None |
| Find the `moon` executable | `cli.MoonExecutableLocator`, `settings.MoonExecutableService` | None (light service) |
| Settings of the IDE project | `settings.MoonSettings` | None (light service) |
| Settings page **Settings \| Tools \| Moon Explorer** | `settings.MoonSettingsConfigurable` | `plugin.xml` |
| Start of the `moon` executable | `cli.MoonCli` | None |
| Resource bundle | `MoonExplorerBundle` | `plugin.xml` |

**NOTE:** The layers come from the proposal in the first version of this file. Record each later change to the layers in an ADR in [adr/](adr/).

## 3. Data flow

1. The plugin finds the workspace root. It starts at the IDE project directory and goes up through the parent directories. The workspace root has a `.moon/` directory with a `workspace.*` file.
2. The plugin runs `moon query tasks` in the workspace root. In moon v2, the output of this command is always JSON.
3. The plugin reads the JSON with Gson. The IntelliJ Platform contains Gson. Thus the plugin has no new dependency.
4. The service sets the new state. The tool window shows the tree or a message.

## 4. Find the `moon` executable

All moon commands of the plugin use the same executable.
The plugin examines these locations in this sequence. It uses the first executable that it finds.

1. The path in **Settings | Tools | Moon Explorer**, if the user set a path.
2. `node_modules/.bin/moon` in the workspace root. On Windows, the plugin looks for `moon.exe` and `moon.cmd`.
3. The `shims` and `bin` directories of proto: `$PROTO_HOME`, or `~/.proto` if `PROTO_HOME` is not set.
4. The directories in `PATH`.

The plugin keeps the path setting in the workspace file of the IDE project. Thus the setting is not in version control.
If the plugin cannot find `moon`, the tool window shows the examined locations and a link to the settings page.

## 5. Threading

All calls to the moon CLI run on a background thread in a coroutine.
The UI layer updates Swing components on the EDT only.
Refer to the skill `intellij-plugin-dev` for the full rules.
