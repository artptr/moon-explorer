# Product vision

## 1. Vision statement

A developer in a large moon workspace finds a task and runs it in the IDE.
The developer does not use the terminal and does not read YAML files.

Moon Explorer is a plugin for IntelliJ-based IDEs.
It gives a graphical interface for the [moon](https://moonrepo.dev) build system.

## 2. Users

### 2.1 Main user

The main user is a TypeScript developer who uses WebStorm.
The developer uses a moon v2 workspace with many projects and more than 100 tasks.

Example workspace: 7 application projects, 10 library projects, and more than 10 tasks in each project.
The example workspace is a workspace that the maintainer uses at work. Its data cannot be in this repository.

### 2.2 Other users

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
7. The plugin is for the developer. It does not have AI functions. AI agents can get data about the workspace from the `moon mcp` command of moon. The Model Context Protocol (MCP) server of the IDE can also start run configurations. Thus, AI agents in the IDE can start moon tasks.

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

- A tool window that shows the final configuration of a task after inheritance: command, arguments, dependencies, inputs, outputs, and the source of each value
- Inlay hints in `moon.yml` that show the inherited tasks of the project
- Navigation and validation in moon configuration files
- A run of the tasks that the local changes affect
- A view of the tasks that moon runs before a selected task
- An inspection that finds tasks without a description
- Debug of a task and more options for run configurations
- Filters and saved scopes (section 4.2). The team removed them from the MVP in the meeting on 2026-10-14. The groups by `stack` and the search can be satisfactory.
- Groups by `layer` and by tag in the tree
- The automatic selection of the project when the user opens a different file
- An icon for the language of each project in the tree. The Moonrepo plugin has this function.
- A notification when the version of moon is older than the minimum version. The Moonrepo plugin shows a similar notification for problems in the workspace.
- A report of the last run
- Run icons in the gutter of `moon.yml`. WebStorm has the same function for scripts in `package.json`.
- Test results in the test runner of the IDE

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
4. Users other than the maintainer open issues and give comments.

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
