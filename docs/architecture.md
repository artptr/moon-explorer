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

The `settings` package contains the settings state and the settings page.
The `run` package contains the run configuration for moon tasks. It runs `moon run <target>` in the workspace root.

| Component | Class | Registration |
|-----------|-------|--------------|
| Tool window **Moon** | `ui.MoonToolWindowFactory`, `ui.MoonTasksPanel` | `plugin.xml` |
| Tree nodes, renderer, and the text for the speed search | `ui.TaskTreeModel.kt` | None |
| Action **Refresh** in the group `MoonExplorer.TasksToolbar` | `ui.RefreshTasksAction` | `plugin.xml` |
| Action **Select Opened File** in the group `MoonExplorer.TasksToolbar`, and the search for the project of a file | `ui.SelectOpenedFileAction`, `model.findProjectForFile` | `plugin.xml` |
| State of the tasks for the IDE project | `model.MoonTasksService` | None (light service) |
| Load of the tasks when the IDE project opens | `model.MoonTasksStartupActivity` | `plugin.xml` |
| Automatic refresh after a change of a moon configuration file | `model.MoonConfigFileListener`, `model.MoonConfigFiles` | `plugin.xml` (`projectListeners`) |
| Notification when a refresh fails after a successful load | `ui.RefreshFailureNotifier`, `ui.RefreshFailureNotifierStartupActivity`, notification group `Moon Explorer` | `plugin.xml` for the startup activity and the notification group. `RefreshFailureNotifier` is a light service. |
| Action **Show Refresh Error** in the group `MoonExplorer.TasksToolbar` | `ui.ShowRefreshFailureAction` | `plugin.xml` |
| Load of the tasks and the result state | `model.TasksLoader`, `model.TasksState` | None |
| Parser of `moon query projects` | `model.QueryProjectsParser` | None |
| Category (`TaskCategory`) and main group (`TaskGrouping`) of the tree, and the action group `MoonExplorer.GroupBy` | `ui.CategoryNoneAction`, `ui.CategoryStackAction`, `ui.CategoryLayerAction`, `ui.GroupByProjectAction`, `ui.GroupByTaskNameAction` | `plugin.xml` |
| Find the workspace root | `cli.MoonWorkspaceLocator` | None |
| Find the `moon` executable | `cli.MoonExecutableLocator`, `settings.MoonExecutableService` | None (light service) |
| Settings of the IDE project | `settings.MoonSettings` | None (light service) |
| Settings page **Settings \| Tools \| Moon Explorer** | `settings.MoonSettingsConfigurable` | `plugin.xml` |
| Run configuration type **moon** | `run.MoonRunConfigurationType`, `run.MoonRunConfiguration` | `plugin.xml` |
| Make and run a configuration for a task | `run.MoonTaskRunner` | None |
| Actions **Run** and **Create Run Configuration** in the group `MoonExplorer.TaskPopup` | `ui.RunTaskAction`, `ui.CreateRunConfigurationAction` | `plugin.xml` |
| Start of the `moon` executable | `cli.MoonCli` | None |
| Resource bundle | `MoonExplorerBundle` | `plugin.xml` |

**NOTE:** The layers come from the proposal in the first version of this file. Record each later change to the layers in an ADR in [adr/](adr/).

## 3. Data flow

1. The plugin finds the workspace root. It starts at the IDE project directory and goes up through the parent directories. The workspace root has a `.moon/` directory with a `workspace.*` file.
2. The plugin runs `moon query projects` in the workspace root. In moon v2, the output of this command is always JSON.
3. The plugin reads the JSON with Gson. The IntelliJ Platform contains Gson. Thus the plugin has no new dependency.
4. The service sets the new state. The tool window shows the tree or a message. If the load fails after a successful load, the tree keeps the last tasks. Refer to section 6.
5. When a moon configuration file changes, the plugin starts a new load. Refer to section 6.

**NOTE:** The `cli` and `model` packages give the cause of a failure as a type, for example `MoonFailure.ExitCode` or `OutputProblem.NoProjects`. `TasksLoader` changes each `CliFailure` into a `MoonFailure` of the model layer. Thus the `ui` package does not use the `cli` package. The `ui` package gets the text for each cause from `MoonExplorerBundle`. Some messages also contain an error text from moon, from the IntelliJ Platform, or from the JSON parser. The bundle message contains this text without change.

The output of `moon query projects` contains the `stack` value, the `layer` value, and the tasks of each project. Thus the plugin starts only one moon process for each load.
The key `tasks` in the output does not contain internal tasks. moon writes internal tasks only in `taskTargets` and in the configuration of the project. The output of `moon query tasks` also does not contain internal tasks.
moon does not write the key `tasks` for a project without tasks. The tree does not show these projects.

The tree has two selections in the **Group By** menu. A separator divides them. Each selection does not change the other selection:

- **Main group**: **Project** or **Task Name**. This selection sets the structure in each category node.
- **Category**: **None**, **Stack**, or **Layer**. If the category is not **None**, the tree has a node for each value at the top. The projects without a value are in the last node, "(none)".

The plugin keeps the two selections in the workspace file of the IDE project. The plugin changes the setting of version 0.1.0 to the two selections. The plugin changes the old value **Stack** to the category **Stack** and the main group **Project**.

## 4. Find the `moon` executable

All moon commands of the plugin use the same executable.
The plugin examines these locations in this sequence. It uses the first executable that it finds.

1. The path in **Settings | Tools | Moon Explorer**, if the user set a path.
2. `node_modules/.bin/moon` in the workspace root. On Windows, the plugin examines `moon.exe`, `moon.cmd`, `moon.bat`, and `moon.com`.
3. The `shims` and `bin` directories of proto: `$PROTO_HOME`, or `~/.proto` if `PROTO_HOME` is not set.
4. The directories in `PATH`.

A `~` at the start of the path in the settings or of `PROTO_HOME` is the home directory of the user.
A relative path in the settings starts at the workspace root.
On Windows, the plugin uses only files with the extension `exe`, `cmd`, `bat`, or `com`. Windows cannot start a file without an extension.
If the path in the settings is not valid or is not an executable file, the plugin examines the next locations. The settings page shows the executable that the plugin uses.

The plugin keeps the path setting in the workspace file of the IDE project. Thus the setting is not in version control.
If the plugin cannot find `moon`, the tool window shows the examined locations and a link to the settings page.

## 5. Time limit to load the tasks

The plugin stops `moon query projects` if it does not complete in the time limit. The tool window then shows a message and a link to the settings page.
`moon run` has no time limit. The task runs in the Run tool window until it stops or the user stops it.

The user sets the time limit in seconds in **Settings | Tools | Moon Explorer**. The default value is 60 seconds.
The settings page accepts only a whole number from 1 to 86400. In milliseconds, the maximum value is less than the maximum value of a Kotlin `Int`.
The plugin keeps the time limit in the workspace file of the IDE project.

`cli.MoonCli` does not read the settings, because the CLI layer does not use the `settings` package. `model.MoonTasksService` reads the time limit from `settings.MoonSettings` and gives it to `MoonCli`.

## 6. Automatic refresh

The plugin refreshes the tree when a moon configuration file changes. These files apply:

- The `moon.*` file of each project, for example `apps/web/moon.yml`.
- All files in `.moon/` in the workspace root, for example `.moon/workspace.yml` and `.moon/tasks/all.yml`.
- A directory that contains a project, if you delete, move, or rename it.

Files in `.moon/cache/` do not apply. moon writes these files when it runs a task.
A `.moon/workspace.*` file in the IDE project directory or in one of its parent directories also applies. A new file of this type can make a workspace or a nearer workspace root. If a load finds no workspace, only this file applies.

`model.MoonConfigFileListener` gets the changes from the VFS. The VFS also sends the changes from outside the IDE, for example from `git checkout`.

The listener does not read files and does not start moon. It calls `MoonTasksService.scheduleRefresh`. The service gives the root to the listener before moon starts. Thus a change during a load also causes a refresh.

The service waits 1 second. If more changes occur in this time, the service starts to wait again. Then it runs `moon query projects` one time.
A change in an IDE editor applies when the IDE saves the document. moon reads only the files on the disk.

**NOTE:** If `git checkout` makes a new project directory, the VFS can send an event only for the directory. Then the plugin does not see the new `moon.yml` file. A later change or a manual refresh corrects the tree.

If a load fails after a successful load, the service gives the state `TasksState.Stale`. This state contains the last successful load and the error. This rule applies to all loads: at startup, after **Refresh**, and after a change.

- The tree shows the tasks of the last successful load. The expanded nodes and the selected nodes do not change.
- `ui.RefreshFailureNotifier` shows a notification with the message for the failure. The notification has the action **Refresh**. The notification also has **Open Settings** if the plugin cannot find or start moon, or if moon did not complete in the time limit.
- The plugin shows a maximum of one notification. A new failure replaces it. A successful load closes it.
- While the state is `Stale`, the toolbar shows the action **Show Refresh Error** with a warning icon. The action shows the notification again.

If a load finds no workspace, the old tasks are not correct. The tool window then shows a message and the plugin closes the notification.
If a load fails and there was no successful load before it, the tool window shows the message for the failure instead of the tree.

## 7. Threading

All calls to the moon CLI run on a background thread in a coroutine.
The UI layer updates Swing components on the EDT only.
Refer to the skill `intellij-plugin-dev` for the full rules.
