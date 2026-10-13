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
- The user searches for a task by its name or its description.

### 4.3 Run a task

The user runs a task from the tree.
The plugin runs the task as a run configuration of the IDE.

### 4.4 Settings

The user sets the path to the `moon` executable.
The plugin uses this path for all moon CLI commands.
The other functions do not operate without a correct path.

## 5. Principles

1. The moon CLI is the only source of data about the workspace. The plugin does not calculate task inheritance itself.
2. The plugin operates without a network connection. It does not send telemetry.
3. The plugin operates with moon v2. The minimum version that the team tests is moon 2.2.3.
4. The plugin uses only the IntelliJ Platform. WebStorm and the JavaScript plugin are not necessary.
5. The plugin uses the standard functions of the IDE, for example tool windows and run configurations.
6. The repository contains a test workspace with sample data. Tests and examples use this workspace. They do not use data from a company.

## 6. First release

The first release contains these functions:

1. A task tree in the **Moon** tool window
2. A selection of the tree groups: by project, by task name, by `stack`, by `layer`, or by tag
3. Filters and saved scopes
4. A search for tasks by name and by description
5. A run of a task from the tree. The user double-clicks a task, and the plugin runs it.
6. A setting for the path to the `moon` executable

Each function will have a GitHub issue. Refer to the GitHub workflow in [AGENTS.md](../../AGENTS.md) for the labels and the project board.

## 7. Later releases

These functions are possible for a later release. The team did not approve them.

- A tool window that shows the final configuration of a task after inheritance: command, arguments, dependencies, inputs, outputs, and the source of each value
- Inlay hints in `moon.yml` that show the inherited tasks of the project
- Navigation and validation in moon configuration files
- A run of the tasks that the local changes affect
- A view of the tasks that moon runs before a selected task
- An inspection that finds tasks without a description
- Debug of a task and more options for run configurations

## 8. Out of scope

- Operation with moon v1
- Changes to moon itself
- A complete view of the project graph. moon has the command `moon project-graph` for this.
- Operation with build systems other than moon

## 9. Targets

Target 1 is a test that the team can measure. Targets 2 and 3 do not have numbers yet. The team will set numbers for them after the first release.

1. In the example workspace, the user finds and runs a task in about 10 seconds. The user does not use the terminal.
2. Users install the plugin from JetBrains Marketplace.
3. Users other than the maintainer open issues and give comments.

## 10. Evidence

The team got this information in a planning meeting on 2026-10-13:

- The maintainer uses moon in an IntelliJ-based IDE every day. Users in the community also asked for the plugin.
- The commands `moon query tasks` and `moon task <project>:<task> --json` give the source of each task. Thus the plugin can use the moon CLI as the only source of data.
- The moon VS Code extension (moon console) shows a tree of projects and tasks. It does not put tasks into groups by task name and does not have saved scopes.
- A web search found no moon plugin for IntelliJ-based IDEs. Issue [#5](https://github.com/artptr/moon-explorer/issues/5) records a manual check of JetBrains Marketplace.
