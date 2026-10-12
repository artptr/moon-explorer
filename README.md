# Moon Explorer

[![Build](https://github.com/artptr/moon-explorer/actions/workflows/build.yml/badge.svg)](https://github.com/artptr/moon-explorer/actions/workflows/build.yml)
[![Docs](https://github.com/artptr/moon-explorer/actions/workflows/docs.yml/badge.svg)](https://github.com/artptr/moon-explorer/actions/workflows/docs.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**Moon Explorer** brings [moon](https://moonrepo.dev) — the task runner and monorepo management tool — into IntelliJ IDEA and other JetBrains IDEs.
Think of it as [Nx Console](https://nx.dev/getting-started/editor-setup), but for moon: browse the projects and tasks in your workspace and run them without leaving the IDE.

> **Status:** early development. There's no release yet, and the feature set is still being shaped.
> Have an idea? [Open a feature request](https://github.com/artptr/moon-explorer/issues/new?template=feature_request.yml).

## What's planned

The exact feature list will come out of an upcoming brainstorm, but the direction looks like this:

- A tool window with your moon projects and their tasks
- One-click run configurations for moon tasks
- A visual project graph
- Smarter editing for `moon.yml` and `.moon/*.yml`

See [docs/product/vision.md](docs/product/vision.md) for more.

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
