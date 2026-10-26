# Moon Explorer

[![Build](https://github.com/artptr/moon-explorer/actions/workflows/build.yml/badge.svg)](https://github.com/artptr/moon-explorer/actions/workflows/build.yml)
[![Docs](https://github.com/artptr/moon-explorer/actions/workflows/docs.yml/badge.svg)](https://github.com/artptr/moon-explorer/actions/workflows/docs.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**Moon Explorer** brings [moon](https://moonrepo.dev) — the task runner and monorepo management tool — into IntelliJ IDEA and other JetBrains IDEs.
Think of it as [Nx Console](https://nx.dev/getting-started/editor-setup), but for moon: browse the projects and tasks in your workspace and run them without leaving the IDE.

> **Status:** early development. There's no release yet, and the feature set is still being shaped.
> Have an idea? [Open a feature request](https://github.com/artptr/moon-explorer/issues/new?template=feature_request.yml).

## What's planned

The goal: in a big moon workspace, find any task and run it without the terminal or digging through YAML.
The first release (an MVP) focuses on that:

- A task tree in the **Moon** tool window, grouped by project or by task name, optionally inside `stack` or `layer` nodes
- Just start typing in the tree to search task IDs (like `build`), project IDs, and descriptions
- Double-click a task to run it as a regular IDE run configuration
- Find and run tasks from **Search Everywhere** (double Shift), or type `moon` in **Run Anything** (double Ctrl) to get target completion and run any moon command — the tool window doesn't even need to be open
- Works out of the box when moon is an npm dev dependency — no need to install it globally

It targets moon v2, works fully offline, and sends no telemetry.
It's especially handy when you're new to a workspace and don't know the task names yet.

See [docs/product/vision.md](docs/product/vision.md) for more.

## Coming from the moon VS Code extension?

Here's where to find each feature of the official [moon console](https://marketplace.visualstudio.com/items?itemName=moonrepo.moon-console) extension (version 0.16.6) in Moon Explorer.
Some features live in the IDE itself rather than in the plugin.

| VS Code extension | Moon Explorer |
|-------------------|---------------|
| Project and task tree, grouped by `stack` and `layer` | The **Moon** tool window. Group by project or by task name, optionally inside `stack` or `layer` nodes (**Group By** menu). |
| Projects grouped by tag | Not available |
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
| Validation of moon config files with JSON schemas | The IDE picks up the moon schemas from [SchemaStore](https://www.schemastore.org). For `template.yml`, add a JSON schema mapping by hand. |
| MCP server for AI agents | Not available |
| Tree updates when `moon.yml` or a file in `.moon/` changes | Automatic, about 1 second after the change |
| Refresh command | The **Refresh** button in the tool window toolbar |
| Open a project's config file | Select a project or a task and press **Jump to Source** (F4 or Cmd+Down). This also opens files in `.moon/tasks/`. |
| `moon.binPath` setting | **Settings \| Tools \| Moon Explorer**. Usually you don't need it: the plugin finds moon in `node_modules/.bin/`, in proto, and on `PATH`. |
| `moon.logLevel` setting | No setting. Add `--log` to a command in **Run Anything**, like `moon --log debug run web:build`. |
| Command to open the settings | **Settings \| Tools \| Moon Explorer** |
| Several root folders in one window | One moon workspace for each IDE project |
| `moon.rootPrefixes` setting | No setting. The plugin looks for the workspace root in the project directory and its parent directories. |

Missing something you rely on? [Open a feature request](https://github.com/artptr/moon-explorer/issues/new?template=feature_request.yml).

## Building from source

You'll need JDK 17+ to launch Gradle; the build itself pulls in JDK 21 automatically.

```bash
./gradlew build      # compile and test
./gradlew runIde     # start a sandbox IDE with the plugin installed
```

More details live in [docs/development.md](docs/development.md).

## Contributing

Contributions are welcome — from people and from AI agents working on their behalf.
Start with [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

A heads-up: the technical docs in this repo are written in
[ASD-STE100 Simplified Technical English](docs/ste/guide.md).
This README is the exception — it's meant to be a friendly front door.

## License

[Apache License 2.0](LICENSE)
