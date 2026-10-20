<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Moon Explorer Changelog

## [Unreleased]

### Changed

- The **Group By** menu of the **Moon** tool window has two selections. The main group (**Project** or **Task Name**) sets the structure of the tree. Below it, **Category** (**None**, **Stack**, or **Layer**) adds a node for each value at the top of the tree. The projects without a value are in the "(none)" node.
- If the old setting is **Stack**, the plugin changes it to the category **Stack** and the main group **Project**.

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

[Unreleased]: https://github.com/artptr/moon-explorer/compare/0.1.0...HEAD
[0.1.0]: https://github.com/artptr/moon-explorer/commits/0.1.0
