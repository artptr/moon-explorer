<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Moon Explorer Changelog

## [Unreleased]

### Added

- **Search Everywhere** finds moon tasks by the task ID, the project ID, or the description. The tasks are in the **All** tab, in the group "moon Tasks". When you select a task, the plugin runs it with a run configuration.
- After `moon run`, **Run Anything** shows the targets of the moon tasks. A target has the form `<project>:<task>`. When you run `moon run <target>`, the plugin runs the target with a run configuration. **Run Anything** runs other moon commands as usual.
- **Search Everywhere** and **Run Anything** use the tasks that the plugin loaded. They also operate when the **Moon** tool window is closed.
- Plugin suggestion: if an IDE project has `@moonrepo/cli` in `package.json`, the IDE suggests Moon Explorer. Only IDEs that have the JavaScript plugin show this suggestion. An example is WebStorm.

## [0.2.0] - 2026-10-22

### Added

- **Select Opened File** action in the toolbar of the **Moon** tool window. The action selects the project of the file in the editor and expands it. For a file in a nested project, the action selects the nested project. The action is available when the main group is **Project**.
- The tree of the **Moon** tool window refreshes automatically 1 second after a change to a `moon.yml` file or to a file in `.moon/`. Files in `.moon/cache/` do not cause a refresh. Changes from outside the IDE, for example from `git checkout`, also cause a refresh.

### Changed

- The **Group By** menu of the **Moon** tool window has two selections. The main group (**Project** or **Task Name**) sets the structure of the tree. Below it, **Category** (**None**, **Stack**, or **Layer**) adds a node for each value at the top of the tree. The projects without a value are in the "(none)" node.
- If the old setting is **Stack**, the plugin changes it to the category **Stack** and the main group **Project**.
- If a refresh fails after a successful load, the tree keeps the last tasks. A notification shows the message for the failure. While the tree shows old tasks, the toolbar shows the **Show Refresh Error** action with a warning icon.
- The **Moon** tool window has new error messages. They tell if the plugin cannot start moon or cannot read its output. They also tell if moon stops without an error text. If the plugin cannot start moon, the message has a link to the settings. For an unexpected error, the message refers to the IDE log.

## [0.1.0] - 2026-10-16

### Added

- **Moon** tool window with a tree of the projects and tasks of the moon workspace, and a **Refresh** action
- The plugin stops `moon query projects` if it does not complete in the time limit. The **Moon** tool window then shows a message and a link to the settings.
- The plugin finds the `moon` executable in `node_modules/.bin/` of the workspace, in the proto directories, and in `PATH`
- Settings page **Settings | Tools | Moon Explorer** with the path to the `moon` executable
- Setting for the time limit to load the tasks in **Settings | Tools | Moon Explorer**. The default value is 60 seconds. The time limit does not apply to the tasks that you run.
- Run configuration type **moon** that runs `moon run <target>`
- Run a task from the **Moon** tool window with a double-click, the **Enter** key, or the context menu. The context menu also has **Create Run Configuration**.
- **Group By** control in the toolbar of the **Moon** tool window. The tree puts the tasks into groups by project, by task name, or by `stack`. The plugin keeps the selected mode.
- Speed search in the tree of the **Moon** tool window. Select the tree. Then type text to find a task by its ID, its project, or its description.

[Unreleased]: https://github.com/artptr/moon-explorer/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/artptr/moon-explorer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/artptr/moon-explorer/commits/v0.1.0
