# Moon Explorer

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/35204-moon-explorer.svg?label=marketplace)](https://plugins.jetbrains.com/plugin/35204-moon-explorer)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/35204-moon-explorer.svg)](https://plugins.jetbrains.com/plugin/35204-moon-explorer)
[![Build](https://github.com/artptr/moon-explorer/actions/workflows/build.yml/badge.svg)](https://github.com/artptr/moon-explorer/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**Moon Explorer is a full IDE companion for [moon](https://moonrepo.dev) workspaces in IntelliJ IDEA, WebStorm, and every other IntelliJ-based JetBrains IDE.**

Browse every project and task in your monorepo, run any of them in a couple of keystrokes, and edit `moon.yml` and `.moon/tasks/` knowing exactly which projects each change will touch. No terminal, no digging through YAML, no global installation of moon.

- **For everyday developers:** find a task by name or description and run it, even in a workspace you joined yesterday.
- **For workspace maintainers:** see inherited tasks, merges, and dependents right in the config files before you change a global task.

<p>
  <a href="https://plugins.jetbrains.com/plugin/35204-moon-explorer">
    <img src="https://img.shields.io/badge/Get%20from-JetBrains%20Marketplace-000000?style=for-the-badge&logo=jetbrains" alt="Get from JetBrains Marketplace">
  </a>
</p>

## Contents

- [Getting started](#getting-started)
- [Features](#features)
  - [Explore the workspace](#explore-the-workspace)
  - [Run tasks from anywhere](#run-tasks-from-anywhere)
  - [Understand task inheritance](#understand-task-inheritance)
  - [Works with your setup](#works-with-your-setup)
- [Settings](#settings)
- [Troubleshooting](#troubleshooting)
- [Privacy and error reports](#privacy-and-error-reports)
- [Coming from the moon VS Code extension?](#coming-from-the-moon-vs-code-extension)
- [Building from source](#building-from-source)
- [Contributing](#contributing)

## Getting started

**Requirements**

- Any IntelliJ-based IDE, version 2025.3 or later: IntelliJ IDEA, WebStorm, PyCharm, GoLand, RustRover, and the rest
- A moon v2 workspace (tested with moon 2.2.3 and later)
- The YAML plugin that comes bundled with the IDE, for the editor features. Everything else works without it.

**Install**

1. Open **Settings | Plugins | Marketplace**.
2. Search for **Moon Explorer** and click **Install**.

Or install it from the [JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/35204-moon-explorer).

**First run**

1. Open the folder that contains your `.moon/` directory (or any folder inside the workspace).
2. Open the **Moon** tool window on the right side of the IDE. The plugin finds moon and loads the projects and tasks.
3. Double-click a task. It runs as a regular IDE run configuration, and its output appears in the **Run** tool window.

That's it. There's nothing to configure when moon is an npm dev dependency, a proto tool, or on your `PATH`.

## Features

### Explore the workspace

The **Moon** tool window shows every project and task in the workspace as a tree.

- **Group the tree the way you think.** Group by project, or by task name to see every project that has a `lint` or a `build` task. Add an outer category by `stack`, `layer`, or tag to split frontend from backend, apps from libraries.
- **Type to search.** Select the tree and start typing to match task IDs, project IDs, and task descriptions.
- **Select Opened File** finds the project of the file in the editor, nested projects included. It's available when a local file is open and the tree is grouped by project.
- **Jump to Source** (F4 or Cmd+Down) opens the `moon.yml` of a project, or the exact line where a task is declared, including tasks inherited from `.moon/tasks/`.
- **Always up to date.** The tree refreshes about a second after you change a project configuration file (for example, `moon.yml`) or a configuration file in `.moon/` (except the cache), including changes from `git checkout` or other tools. If a refresh fails, you keep the last good tree, and the **Show Refresh Error** button tells you what went wrong.
- Internal tasks stay hidden in the tree, the same as in `moon run`.

### Run tasks from anywhere

- **From the tree:** double-click or press Enter on a task. Select several tasks to run them in one `moon run` command.
- **Across projects:** on a task-name node, use **Run in N Projects** or **Run Affected in N Projects**. The second one adds `--affected`, so moon runs the task only where your changes matter.
- **From Search Everywhere** (double Shift): find any task by ID, project, or description and press Enter. The tool window doesn't even need to be open.
- **From Run Anything** (double Ctrl): type `moon` to get completion for every `moon run <project>:<task>` target. Any other moon command works too, such as `moon check web`, `moon project-graph`, or `moon query projects`, with the moon executable the plugin found.
- **From the toolbar:** click **Execute Moon Task** in the **Moon** tool window to open **Run Anything** with `moon ` already typed, then pick a target.
- **From the editor:** task keys in `moon.yml` have run icons in the gutter. In `.moon/tasks/`, the icon runs the global task in every project that inherits it, only the affected ones, or one project you pick. Renames, includes, and excludes in `workspace.inheritedTasks` are respected.
- **Full run configurations.** Each run is a standard IDE run configuration of type **moon**. Right-click a task and choose **Edit '\<target\>' Settings...** to save it, share it, and adjust it:
  - several targets in one configuration
  - **Run only the affected tasks** (`--affected`)
  - **Run again without the cache** (`--force`)
  - arguments passed through to the task command, such as `moon run web:test -- src/app.test.ts`
  - environment variables

### Understand task inheritance

Global tasks in `.moon/tasks/` change the tasks of many projects at once. Moon Explorer shows the effect right in the editor, so you can see the impact before you commit a change.

**In a file in `.moon/tasks/`:**

- A Code Vision hint at the top of the file: **Inherited by N projects**. Click it for the list and jump to any project.
- **Merged in N projects** on a global task that a project overrides or extends. A gutter icon lists each project and its non-default merge strategies, for example `web (args: prepend, deps: replace)`.
- **N dependents** on a global task that other tasks depend on, grouped by target and jumping straight to the dependent task's key.

**In the `moon.yml` of a project:**

- **N inherited tasks** above the `tasks` key (or at the top of the file when there's no `tasks` key), so you can see what the project gets for free.
- **Merged with \<global file name\>** (for example, **Merged with all.yml**) on a local task that merges with a global one. The gutter icon jumps to the global task, or opens a list when the task merges with several files or tasks.
- In `workspace.inheritedTasks`, completion suggests the global task IDs that the project can inherit, and **Go to Declaration** opens the global task.
- An inspection flags `include`, `exclude`, and `rename` entries that match no global task (a typo like `lnt`, for example), with a quick fix to remove them.

`moon.yml` and `moon.yaml` get their own **Moon** file type with the moon icon. The IDE's existing JSON schema support is unaffected. One side effect: **Search Everywhere** no longer lists the YAML keys of these files.

These editor features work with YAML configuration files. `moon.json` and the other formats get no run icons or hints, and **Jump to Source** opens them at the first line.

The task data comes from the moon CLI itself (`moon query projects` and `moon task-graph`). The plugin reads your configuration files to connect that data to the editor: to place the hints and icons, and to provide completion, navigation, and the inspection. It never guesses at inheritance rules, so what you see is what moon will run.

### Works with your setup

- **Finds moon automatically.** It looks in `node_modules/.bin/` of the workspace first, then in proto, then on `PATH`. No global install needed.
- **Any IntelliJ-based JetBrains IDE.** It uses only the IntelliJ Platform. WebStorm and the JavaScript plugin are optional.
- **Native IDE building blocks.** Tool windows, run configurations, Search Everywhere, Run Anything, Code Vision, inspections. Your shortcuts and habits carry over.
- **Works offline** and sends no telemetry.

## Settings

Open **Settings | Tools | Moon Explorer**, or click the gear in the **Moon** tool window toolbar.

| Setting | Default | What it does |
|---------|---------|--------------|
| Path to moon executable | empty (automatic search) | A path to use before the automatic search. A relative path starts at the workspace root. The page also shows which executable the plugin uses now. |
| Time limit to load the tasks | 60 seconds | The limit for each moon command the plugin runs to load the tasks. It doesn't apply to tasks that you run. Raise it if moon downloads a toolchain on the first run. |

The editor features have the usual IDE switches:

- Code Vision hints: **Settings | Editor | Inlay Hints**
- The `workspace.inheritedTasks` inspection: **Settings | Editor | Inspections | Moon**

## Troubleshooting

**"The IDE project does not contain a moon workspace."**
The plugin looks for `.moon/` in the project directory and its parent directories. Open a folder inside the moon workspace.

**"Cannot find the moon executable."**
The message lists every location the plugin checked. Install moon (for example `npm install -D @moonrepo/cli`), or set the path in the settings, then click **Refresh**.

**Loading times out.**
The first moon command in a fresh clone can take a while if moon installs a toolchain. Raise the time limit in the settings.

**A task runs with the wrong Node.js, or moon can't find Node.js.**
If you turned off **Include system environment variables** in a run configuration, add `PATH` to its environment variables. The npm package of moon uses `PATH` to find Node.js.

**A new task has no run icon or hint yet.**
The editor features use the tasks that the plugin loaded. They update after the next refresh, which happens about a second after you save the file.

For anything else, [open an issue](https://github.com/artptr/moon-explorer/issues/new/choose).

## Privacy and error reports

Moon Explorer runs offline and collects no telemetry. It reads only local data: the output of the moon CLI and your configuration files. The only network action is the error report that you choose to send, as described below.

If the plugin crashes or freezes the IDE, you can send a report to the maintainer: in the **IDE Internal Errors** dialog, click **Report to third-party plugin**.
Reports go to JetBrains Marketplace, and only with your consent: either you send each one yourself, or you've turned on **Send error reports to JetBrains automatically** in the IDE.
A report holds the stack trace or thread dumps, plus the IDE and plugin versions. It helps a lot with freezes that only happen on your machine.

## Coming from the moon VS Code extension?

Here's where to find each feature of the official [moon console](https://marketplace.visualstudio.com/items?itemName=moonrepo.moon-console) extension (version 0.16.6) in Moon Explorer.
Some features live in the IDE itself rather than in the plugin.

| VS Code extension | Moon Explorer |
|-------------------|---------------|
| Project and task tree, grouped by `stack` and `layer` | The **Moon** tool window. Group by project or by task name, optionally inside `stack`, `layer`, or tag nodes (**Group By** menu). |
| Projects grouped by tag | **Group By \| Tag** |
| Language icon for each project | Not available |
| Type icon for each task | Not available |
| Task command shown in the tree | The tree shows the task description. Press **Jump to Source** (F4 or Cmd+Down) to see the task in `moon.yml`. |
| Internal tasks hidden from the tree | Hidden too |
| `moon.hideTasks` setting | Not available |
| Run a task from the tree | Double-click the task. It runs as a regular IDE run configuration. |
| Run a target you type, like `web:build` | Type `moon run web:build` in **Run Anything** (double Ctrl), with target completion. Or find the task in **Search Everywhere** (double Shift). |
| Run `moon check` for a project | Type `moon check <project>` in **Run Anything** |
| Report of the last run | Not available. The **Run** tool window shows the output of each run. |
| Action, project, and task graphs | Type `moon action-graph`, `moon project-graph`, or `moon task-graph` in **Run Anything** |
| Validation of moon config files with JSON schemas | Partial. Click **Apply schema** in each file that shows the banner. Files directly in `.moon/tasks/` and `template.yml` get no schema. Offline, only schemas in the IDE cache work. Workaround: map the files to the local schemas in `.moon/cache/schemas/` by hand. |
| MCP server for AI agents | Not available in the plugin. Use `moon mcp`, or the IDE's own MCP server to start run configurations. |
| Tree updates when `moon.yml` or a file in `.moon/` changes | Automatic, about 1 second after a change to a project configuration file (for example, `moon.yml`) or a configuration file in `.moon/` (except the cache) |
| Refresh command | The **Refresh** button in the tool window toolbar |
| Open a project's config file | Select a project or a task and press **Jump to Source** (F4 or Cmd+Down). This also opens files in `.moon/tasks/`. |
| `moon.binPath` setting | **Settings \| Tools \| Moon Explorer**. Usually you don't need it: the plugin finds moon in `node_modules/.bin/`, in proto, and on `PATH`. |
| `moon.logLevel` setting | No setting. Add `--log` to a command in **Run Anything**, like `moon --log debug run web:build`. |
| Command to open the settings | **Settings \| Tools \| Moon Explorer**, or the gear in the tool window toolbar |
| Several root folders in one window | One moon workspace for each IDE project |
| `moon.rootPrefixes` setting | No setting. The plugin looks for the workspace root in the project directory and its parent directories. |

And a few things only Moon Explorer does: grouping by task name, running a task across many projects at once, and inheritance, merge, and dependents hints in the config files.

Missing something you rely on? [Open a feature request](https://github.com/artptr/moon-explorer/issues/new?template=feature_request.yml).

## Building from source

You'll need JDK 17+ to launch Gradle; the build itself pulls in JDK 21 automatically.

```bash
./gradlew build      # compile and test
./gradlew runIde     # start a sandbox IDE with the plugin installed
```

More details live in [docs/development.md](docs/development.md), and the design is in [docs/architecture.md](docs/architecture.md).
See [CHANGELOG.md](CHANGELOG.md) for what changed in each release, and [docs/product/vision.md](docs/product/vision.md) for where the project is headed.

## Contributing

Bug reports, feature requests, and pull requests are all welcome, from people and from AI agents working on their behalf.
Start with [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

A heads-up: the technical docs in this repo are written in
[ASD-STE100 Simplified Technical English](docs/ste/guide.md).
This README is the exception: it's meant to be a friendly front door.

## License

[Apache License 2.0](LICENSE)
