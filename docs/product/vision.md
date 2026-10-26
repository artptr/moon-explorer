# Product vision

## 1. Vision statement

A developer in a large moon workspace finds a task and runs it in the IDE.
The developer does not use the terminal and does not read YAML files.

A workspace maintainer changes a task in the moon configuration files in the IDE.
Before the change, the workspace maintainer sees which projects inherit the task and which tasks are its dependents.

Moon Explorer is a plugin for IntelliJ-based IDEs.
It gives a graphical interface for the [moon](https://moonrepo.dev) build system.

## 2. Users

**NOTE:** In this document, "the maintainer" is the maintainer of Moon Explorer. The workspace maintainer is a user of the plugin.

### 2.1 Main user

The main user is a TypeScript developer who uses WebStorm.
The developer uses a moon v2 workspace with many projects and more than 100 tasks.

Example workspace: 7 application projects, 10 library projects, and more than 10 tasks in each project.
The example workspace is a workspace that the maintainer uses at work. Its data cannot be in this repository.

### 2.2 Workspace maintainer

The workspace maintainer is a developer who changes the moon configuration of the workspace.
The changes include the global tasks in `.moon/tasks/` and the local tasks in the `moon.yml` files of the projects.
Section 10 gives data from the Git history of the example workspace from 2026-04-23 to 2026-10-23.
In this period, fewer than 3% of the contributors made about 80% of the counted commits that change `moon.yml` files (section 10).
The same contributors made all counted commits that change files in `.moon/`.

A change to a global task can change the tasks of many projects.
Before a change to a global task, the workspace maintainer must examine:

- The global task in `.moon/tasks/`
- The `moon.yml` files of the projects that inherit or merge the global task
- The `moon.yml` files of the projects with tasks that are dependents of the global task

The workspace maintainer is not the main user.
But the workspace maintainer can have an effect on the tools of the other developers in the workspace.
It is possible that a workspace maintainer who uses the plugin tells the other developers about it. The team did not do a check of this.

### 2.3 Other users

- Developers in a team with different stacks, for example frontend and backend. Each developer uses only the tasks of one part of the workspace.
- Developers who are new to a moon workspace and must find which tasks it has.
- Developers who use other IntelliJ-based IDEs, for example IntelliJ IDEA or RustRover.

## 3. Problem

In a large workspace, the user cannot see which tasks are in the workspace.
moon shows the tasks only through CLI commands, and the output is long.
For the example workspace, the output of `moon query tasks` has about 16,000 lines. The maintainer counted the lines.

The user also cannot hide the tasks that are not applicable to the work of the user.
For example, a frontend developer sees the backend tasks in the same list.

The main problem is to find the tasks: "Which tasks does this workspace have?"
These problems are less important for the first release:

- Task details: "What does this task do, and which file has its configuration?"
- Selection: "Which task must I run now?"

The workspace maintainer has a different problem. This problem is not in the scope of the first release.
Before a change to a global task, the workspace maintainer cannot see which projects inherit the task and which tasks are its dependents.
A `moon.yml` file does not show the tasks that the project inherits.
A file in `.moon/tasks/` does not show which projects inherit its tasks, and it does not show the dependents of each task.

## 4. Main functions

### 4.1 See the tasks

The plugin shows all tasks of the workspace in a tree in a tool window.
The user selects how the tree puts the tasks into groups:

- By project: each project contains its tasks.
- By task name: each task name contains the projects that have a task with that name.
- By project metadata: `stack`, `layer`, or tag.

### 4.2 Filter the tasks

The user hides the tasks that are not applicable to the work of the user.

- The user sets a filter on project metadata, for example only the projects that have `stack` set to `frontend`.
- The user saves a filter as a scope. The plugin keeps the scope for the next session.
- The user searches for a task by its ID or its description.

### 4.3 Run a task

The user runs a task from the tree.
The plugin runs the task as a run configuration of the IDE.

### 4.4 Find the moon executable

The plugin finds the `moon` executable automatically.
In a TypeScript workspace, moon is normally an npm development dependency (in `devDependencies`).
Thus the plugin first tries to find `moon` in `node_modules/.bin/` of the workspace root.
Then the plugin tries to find `moon` in the directories of [proto](https://moonrepo.dev/proto) and in `PATH`.

The user can set a different path in the settings. The plugin uses this path before the automatic search.
The plugin uses the same executable for all moon CLI commands.
The other functions do not operate without the `moon` executable.

## 5. Principles

1. The moon CLI is the only source of data about the workspace. The plugin does not calculate task inheritance itself.
2. The plugin operates without a network connection. It does not send telemetry.
3. The plugin operates with moon v2. The minimum version that the team tests is moon 2.2.3.
4. The plugin uses only the IntelliJ Platform. WebStorm and the JavaScript plugin are not necessary.
5. The plugin uses the standard functions of the IDE, for example tool windows and run configurations.
6. The repository contains a test workspace with sample data. Tests and examples use this workspace. They do not use data from a company.
7. The plugin is for developers, for example the main user and the workspace maintainer. It does not have AI functions. AI agents can get data about the workspace from the `moon mcp` command of moon. The Model Context Protocol (MCP) server of the IDE can also start run configurations. Thus, AI agents in the IDE can start moon tasks.

## 6. First release (MVP)

### 6.1 Purpose

The first release is a minimum viable product (MVP). The MVP is a test of this statement:

In a large moon workspace, a WebStorm user finds and runs tasks in the **Moon** tool window each day. The user does not use the terminal or an AI agent for this work.

The plugin helps the user most when the user does not know the task IDs.
For example, the user is new to the workspace. Another example is work in a part of the workspace that is new to the user.
When the user knows the task IDs, the terminal is also a good method.

### 6.2 Functions

The MVP contains these functions. The functions with the priority **Medium** do not prevent the release of version 0.1.

| Priority | Function | Issue |
|----------|----------|-------|
| High | A task tree in the **Moon** tool window | [#6](https://github.com/artptr/moon-explorer/issues/6) (complete) |
| High | An automatic search for the `moon` executable, and a setting for a different path | [#18](https://github.com/artptr/moon-explorer/issues/18) |
| High | A selection of the tree groups: by project, by task name, or by `stack` | [#11](https://github.com/artptr/moon-explorer/issues/11) |
| High | A search in the tree for task IDs, project IDs, and task descriptions | [#19](https://github.com/artptr/moon-explorer/issues/19) |
| High | A run of a task from the tree as a run configuration. The **Run** tool window shows the output as console text. | [#12](https://github.com/artptr/moon-explorer/issues/12) |
| Medium | An automatic refresh of the tree when a moon configuration file changes | [#20](https://github.com/artptr/moon-explorer/issues/20) |
| Medium | An action that finds the project of the open file and selects this project in the tree | [#21](https://github.com/artptr/moon-explorer/issues/21) |

Refer to the GitHub workflow in [AGENTS.md](../../AGENTS.md) for the labels and the project board.

### 6.3 End of the MVP

The MVP is complete when these conditions are true:

1. All functions with the priority **High** are complete.
2. The maintainer publishes version 0.1 on JetBrains Marketplace.
3. The maintainer completes target 2 in section 9.

## 7. Later releases

These functions are possible for a later release. The team did not approve them.
The functions that use the moon configuration files are for the workspace maintainer (section 2.2).

- A tool window that shows the final configuration of a task after inheritance: command, arguments, dependencies, inputs, outputs, and the source of each value
- Navigation and validation in moon configuration files
- A run of the tasks that the local changes affect. Issue [#68](https://github.com/artptr/moon-explorer/issues/68) adds this run for the projects of a task name node in the tree. The team did not approve a run of all affected tasks in the workspace.
- A view of the tasks that moon runs before a selected task
- An inspection that finds tasks without a description
- Debug of a task and more options for run configurations
- Filters and saved scopes (section 4.2). The team removed them from the MVP in the meeting on 2026-10-14. The groups by `stack` and the search can be satisfactory.
- Pinned tasks. The user pins the tasks that the user runs many times. The tree shows the pinned tasks in a group at the top. The team selected pinned tasks in place of hidden tasks in the meeting on 2026-10-19. Refer to section 10. Issue [#75](https://github.com/artptr/moon-explorer/issues/75)
- A search for tasks in **Search Everywhere** and **Run Anything**. The user can find and run a task when the **Moon** tool window is closed. Issue [#76](https://github.com/artptr/moon-explorer/issues/76)
- An action that opens the project graph, the task graph, or the action graph of moon. The commands `moon project-graph`, `moon task-graph`, and `moon action-graph` show the graph. The plugin does not show the graph itself.
- An action that runs `moon check` for a project
- The command of a task in a tooltip in the tree
- Groups by tag in the tree. Issue [#80](https://github.com/artptr/moon-explorer/issues/80) added this function. Refer to section 11.
- The automatic selection of the project when the user opens a different file
- An icon for the language of each project in the tree. The Moonrepo plugin has this function.
- A notification when the version of moon is older than the minimum version. The Moonrepo plugin shows a similar notification for problems in the workspace.
- A report of the last run. Issue [#77](https://github.com/artptr/moon-explorer/issues/77)
- Run icons in the gutter of `moon.yml`. WebStorm has the same function for scripts in `package.json`. Issue [#70](https://github.com/artptr/moon-explorer/issues/70)
- Test results in the test runner of the IDE
- Code Vision hints and gutter icons that show the task inheritance and the task dependents in moon configuration files. In `moon.yml`, the hints show the inherited tasks of the project. Issue [#100](https://github.com/artptr/moon-explorer/issues/100)

## 8. Out of scope

- Operation with moon v1
- Changes to moon itself
- A complete view of the project graph. moon has the command `moon project-graph` for this.
- Operation with build systems other than moon

## 9. Targets

Targets 1 and 2 are tests that the team can measure. Targets 3 and 4 do not have numbers yet. The team will set numbers for them after the MVP.

1. In the example workspace, the user finds and runs a task in about 10 seconds. The user does not use the terminal.
2. The maintainer uses the plugin each day for two weeks. During this period, the maintainer does not use the terminal or an AI agent to find or run moon tasks.
3. Users install the plugin from JetBrains Marketplace.
4. Users other than the Moon Explorer maintainer open issues and give comments.

## 10. Evidence

The team got this information in a meeting on 2026-10-13:

- The maintainer uses moon in an IntelliJ-based IDE each day. Users in the community also asked for the plugin.
- The commands `moon query tasks` and `moon task <project>:<task> --json` give the source of each task. Thus the plugin can use the moon CLI as the only source of data.
- The moon VS Code extension (moon console) shows a tree of projects and tasks. It does not put tasks into groups by task name and does not have saved scopes.
- A manual check of JetBrains Marketplace on 2026-10-13 found one moon plugin. Issue [#5](https://github.com/artptr/moon-explorer/issues/5) records the check.
  - The search for `moonrepo` gave one result. The search for `moon` gave 28 results. Only one of the 28 results is for moon.
  - The plugin is [Moonrepo](https://plugins.jetbrains.com/plugin/32442) (unofficial, source: [mteterel/intellij-moon-plugin](https://github.com/mteterel/intellij-moon-plugin), MIT license).
  - It is at an early stage: version 0.0.1, 52 downloads, and one maintainer.
  - It shows projects, tasks, and templates, and it has a run configuration type, a graph editor, and a report of the last run.
  - Its scope is close to the scope of Moon Explorer. The team must compare the two plugins before it decides the final feature list.

The team got this information in a meeting on 2026-10-14:

- The maintainer reports that many moon users use one of these tools: VS Code with the moon console extension, the terminal, or an AI agent. Users use these tools in place of the plugin. The Moonrepo plugin is not the main tool that users use in place of Moon Explorer.
- moon has the command `moon mcp`. It starts an MCP server that gives data about the workspace to AI agents.
- In the example workspace, moon is an npm development dependency. The test workspace also has moon as an npm development dependency. The first version of the task tree tried to find `moon` only in `PATH`. Thus, it did not find `moon` in these workspaces.
- The repository of mteterel/intellij-moon-plugin has 7 commits. The maintainer of that plugin made 4 commits, all on 2026-06-24. Dependabot made the other 3 commits: one on 2026-06-24, one on 2026-07-10, and one on 2026-08-24. The team can use ideas from this plugin. The team does not plan to work together with its maintainer now.
- For the run of a task, console text in the **Run** tool window is satisfactory. The test runner of the IDE is not necessary for the MVP.
- Developers who are new to the workspace are possible users. The maintainer did not measure how many new developers there are.

The team got this information in a meeting on 2026-10-19:

- The team examined the source code of the moon VS Code extension, version 0.16.6. This version is from 2026-06-08. Section 11 gives the comparison.
- The extension has about 7,300 installations from Visual Studio Marketplace and about 4,100 downloads from Open VSX. The last seven releases do not add functions. They make the extension operate with moon v2, correct problems with moon v2, and correct the release scripts.
- Not many users change from VS Code to an IntelliJ-based IDE. A user who compares the two products is usually a WebStorm user. This user reads the list of functions of the extension. Then the user asks why Moon Explorer does not have one of these functions.
- The maintainer did not use the extension.
- The extension has the setting `moon.hideTasks`. This setting shows that some users hide tasks.
- A hidden task can cause a problem: the user cannot find a task that the user hid. Thus the team selected pinned tasks in place of hidden tasks.
- The team did checks with moon 2.2.3 in the test workspace:
  - `moon query projects` does not write internal tasks in the key `tasks`. `moon run` does not run them. Thus the tree does not show internal tasks.
  - `moon query projects` gives `layer`, `language`, tags, and the command of each task. Groups by `layer` and by tag do not need one more moon command.
  - moon sets the type of a task from its options. A task without outputs that is not persistent has the type `test`. Thus `lint` and `format` have the type `test`.
  - The commands `moon project-graph`, `moon task-graph`, and `moon action-graph` show the graph. The user can operate the graph.
  - moon writes a report of the last run in `.moon/cache/runReport.json`.
- SchemaStore has the JSON schemas of moon v2 for `moon.yml` and for the files in `.moon/`. JetBrains IDEs use SchemaStore by default. SchemaStore has no file pattern for `template.yml`.

The team got this information from the Git history of the example workspace on 2026-10-23.
The data is for the period from 2026-04-23 to 2026-10-23.
The data from the example workspace cannot be in this repository (section 2.1). Thus this section does not give the commit counts.

The team used these commands to get the authors of the commits:

```bash
git log --since=6.months --format='%an' -- '*moon.yml'
git log --since=6.months --format='%an' -- '.moon/'
```

- The filter `*moon.yml` gives the commits that change `moon.yml` files. The filter also gives the commits that change files with other names, for example `foo-moon.yml`. In the example workspace, the filter gave only the commits that change `moon.yml` files.
- The filter `.moon/` gives the commits that change files in `.moon/`. These files include `.moon/tasks/` and also the workspace and toolchain configuration.
- A commit that changes a `moon.yml` file and a file in `.moon/` is in the two counts.
- The commands use the default history simplification of Git. Git does not show a merge commit if the files in the merge are the same as in one of its parents.
- All authors are people. No author is a bot.
- The commands give the author names. If two people have the same name, the data shows one author. If one person uses two names, the data shows two authors.

In this document, a "counted commit" is a commit in the output of these commands.

The results are:

- Fewer than 10% of the contributors are authors of a counted commit that changes a `moon.yml` file. The data does not show how the other contributors use moon.
- Fewer than 3% of the contributors made about 80% of the counted commits that change `moon.yml` files.
- The same contributors made all counted commits that change files in `.moon/`. The maintainer compared the author names in the output of the two commands.
- The counted commits that change files in `.moon/` are fewer than 10% of the counted commits that change `moon.yml` files.
- The maintainer is one of these contributors.
- These contributors are possible examples of the workspace maintainer (section 2.2). The data shows only the authors and the percentages of the commits.
- The data comes from one workspace. The maintainer is the only workspace maintainer who gave comments to the team.

## 11. Comparison with the moon VS Code extension

The moon VS Code extension (moon console) is the official extension of moon for VS Code.
Users compare Moon Explorer with the list of functions of this extension. Refer to section 10.
This table gives the decision for each function of the extension, version 0.16.6.

| Function of the extension | Moon Explorer | Decision |
|---------------------------|---------------|----------|
| A tree of projects and tasks with groups by `stack` and `layer` | Groups by project and by task name, in category nodes for `stack` or `layer` | Complete. Issue [#67](https://github.com/artptr/moon-explorer/issues/67) added the groups by `layer`. |
| A view of the projects in groups by tag | Groups by project and by task name, in category nodes for tags | Complete. Issue [#80](https://github.com/artptr/moon-explorer/issues/80) added the groups by tag. |
| An icon for the language of each project | No | In section 7. |
| An icon for the type of each task | No | The team does not plan this function. Many tasks have the type `test`, for example `lint`. Refer to section 10. |
| The command of each task in the tree | The description of each task | A tooltip with the command is in section 7. |
| Internal tasks are not in the tree | Internal tasks are not in the tree | Complete. moon does not write internal tasks in the key `tasks` in the output. A test makes sure that the key `tasks` does not contain internal tasks. |
| The setting `moon.hideTasks` hides tasks | No | Pinned tasks are in section 7. Issue [#75](https://github.com/artptr/moon-explorer/issues/75) records them. |
| A run of a task from the tree | A run configuration | Complete. |
| A run of a task that the user types, for example `web:build` | **Run Anything** runs `moon run <target>` and gives code completion for the task. **Search Everywhere** finds the tasks. | Complete. Issue [#76](https://github.com/artptr/moon-explorer/issues/76) added this function. |
| A run of `moon check` for a project | **Run Anything** runs `moon check <project>`. | An action in the tree is in section 7. |
| A report of the last run | No | Issue [#77](https://github.com/artptr/moon-explorer/issues/77) records this function. |
| The action graph, the project graph, and the task graph | **Run Anything** runs `moon action-graph`, `moon project-graph`, and `moon task-graph`. | An action that starts the moon commands is in section 7. The plugin does not show the graph itself. Refer to section 8. |
| A check of the moon configuration files with JSON schemas | The IDE gets the schemas from SchemaStore | The team did not do a check of this function in WebStorm. A manual configuration is necessary for `template.yml`. A setting for the local schemas in `.moon/cache/schemas` is possible. Issue [#78](https://github.com/artptr/moon-explorer/issues/78) records the check. |
| An MCP server for AI agents | No | The team does not plan this function. Refer to principle 7 in section 5. |
| An update of the tree when `moon.yml` or a file in `.moon/` changes | An automatic refresh 1 second after the change | Complete. Issue [#20](https://github.com/artptr/moon-explorer/issues/20) records this function. If the refresh fails, the tree keeps the last tasks and a notification shows the error. |
| An action that updates the tree | The **Refresh** action | Complete. |
| An action that opens the configuration file of a project | The **Jump to Source** action for a project or a task | Complete. Issue [#69](https://github.com/artptr/moon-explorer/issues/69) added this function. The action also opens the files in `.moon/tasks/`. |
| The setting `moon.binPath` for the path to the `moon` executable | A setting for the path. The plugin also finds the executable automatically (section 4.4). | Complete. |
| The setting `moon.logLevel` for the log level of all moon commands | No setting. The user can type the option `--log` in a moon command in **Run Anything**. | The team did not examine a setting. |
| A command that opens the settings | The settings page **Settings \| Tools \| Moon Explorer** | Complete. The user opens the page from the **Settings** dialog. The tool window shows a link to the page for some errors. |
| More than one root directory in the VS Code window | The plugin uses one workspace root for each IDE project | The team did not examine this function. |
| The setting `moon.rootPrefixes` for a workspace root in a subdirectory | The plugin finds the workspace root only in the IDE project directory or in its parent directories | The team did not examine this function. |

The README uses this table to tell users which functions Moon Explorer has. Issue [#79](https://github.com/artptr/moon-explorer/issues/79) added a section about the moon VS Code extension to the README.
If you change the column "Moon Explorer", also change the README.
