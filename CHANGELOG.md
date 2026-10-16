<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Moon Explorer Changelog

## [Unreleased]

### Added

- Project structure for the IntelliJ Platform plugin
- **Moon** tool window with a tree of the projects and tasks of the moon workspace, and a **Refresh** action
- The plugin stops the moon process if it does not complete in 60 seconds. The **Moon** tool window then shows a message.
- The plugin finds the `moon` executable in `node_modules/.bin/` of the workspace, in the proto directories, and in `PATH`
- Settings page **Settings | Tools | Moon Explorer** with the path to the `moon` executable
- Run configuration type **moon** that runs `moon run <target>`
- Run a task from the **Moon** tool window with a double-click, the **Enter** key, or the context menu. The context menu also has **Create Run Configuration**.
- **Group By** control in the toolbar of the **Moon** tool window. The tree puts the tasks into groups by project, by task name, or by `stack`. The plugin keeps the selected mode.
