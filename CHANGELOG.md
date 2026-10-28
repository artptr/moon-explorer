<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Moon Explorer Changelog

## [Unreleased]

### Added

- You can send a report of a plugin error to the maintainer. In the **IDE Internal Errors** dialog, click **Report to third-party plugin**. The IDE can also send reports of UI freezes that the plugin causes. The IDE sends the reports to JetBrains Marketplace only if you agree.

## [0.3.0] - 2026-10-26

### Added

- **Search Everywhere** finds moon tasks by the task ID, the project ID, or the description. The tasks are in the **All** tab, in the group "Moon Tasks". When you select a task, the plugin runs it with a run configuration.
- When you type `moon`, **Run Anything** shows `moon run <target>` for each moon task. A target has the form `<project>:<task>`. When you run `moon run <target>`, the plugin runs the target with a run configuration.
- **Run Anything** runs all other moon commands with the moon executable that the plugin finds, in the workspace root. An example is `moon query projects`. Thus these commands also operate when moon is not in `PATH`. The **Run** tool window shows the output. If the IDE project has no moon workspace, **Run Anything** runs moon commands in the usual way.
- The moon icon marks the moon tasks in the **Moon** tool window, in **Search Everywhere**, and in **Run Anything**. In the new UI, the icon is an outline with a light fill, the same as the other node icons. In the classic UI, the icon is solid. The moon run configurations also show this icon. The button of the **Moon** tool window shows a gray outline of the icon, the same as the other tool window buttons.
- **Search Everywhere** and **Run Anything** use the tasks that the plugin loaded. They also operate when the **Moon** tool window is closed. During a refresh, they use the tasks of the last successful load. Thus the results remain when you save a moon configuration file. Each time that you save the file, the plugin starts a refresh.
- A task name node in the **Moon** tool window has the actions **Run in N Projects** and **Run Affected in N Projects**. N is the number of projects in the node. Below a category node, the actions use only the projects of that category. The plugin gives the full list of targets to `moon run`. **Run Affected in N Projects** adds the option `--affected`. Thus moon runs only the tasks that the changed files affect.
- When you select two or more task nodes in the **Moon** tool window, the context menu has the action **Run N Tasks**. This action runs the selected tasks with one `moon run` command.
- A moon run configuration can contain more than one target. Put spaces between the targets. The option **Run only the affected tasks** adds `--affected` to the command.
- The task nodes and the project nodes in the **Moon** tool window have the action **Jump to Source**. Use the shortcut of **Jump to Source** (F4 or Cmd+Down) or the context menu. For a task, the IDE opens the configuration file of the project, for example `moon.yml`, or a file in `.moon/tasks/`. If the task is in more than one file, the IDE shows a list of the files. In a YAML file, the IDE shows the line of the task. In a file of a different format, and for a project, the IDE shows the first line.
- The **Category** selection in the **Group By** menu of the **Moon** tool window has the option **Tag**. The tree shows a node for each tag. A project with two tags is in the two tag nodes. The projects without tags are in the "(none)" node.
- Plugin suggestion: if an IDE project has `@moonrepo/cli` in `package.json`, the IDE suggests Moon Explorer. Only IDEs that have the JavaScript plugin show this suggestion. An example is WebStorm.

### Fixed

- During a refresh after a failed refresh, the toolbar of the **Moon** tool window continues to show the **Show Refresh Error** action. Before, the action was not available until the refresh completed, but the notification of the failure stayed open.

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

[Unreleased]: https://github.com/artptr/moon-explorer/compare/0.3.0...HEAD
[0.3.0]: https://github.com/artptr/moon-explorer/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/artptr/moon-explorer/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/artptr/moon-explorer/commits/0.1.0
