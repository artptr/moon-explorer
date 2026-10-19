# Agent instructions

This file gives instructions to AI coding agents and to human contributors.
Obey these instructions for all work in this repository.

## 1. Project

Moon Explorer is an IntelliJ Platform plugin for [moon](https://moonrepo.dev).
moon is a build system and repository management tool for monorepos.
The plugin has the same function for moon as Nx Console has for Nx.

The project is open source. The license is Apache-2.0.
The project is in an early stage. The feature list is not yet approved.
Refer to [docs/product/vision.md](docs/product/vision.md) for the product scope.

## 2. Repository map

| Path | Content |
|------|---------|
| `src/main/kotlin/io/github/artptr/moonexplorer/` | Plugin source code (Kotlin) |
| `src/main/resources/META-INF/plugin.xml` | Plugin descriptor: ID, extensions, dependencies |
| `src/main/resources/messages/` | User interface text (resource bundles) |
| `src/test/kotlin/` | Tests |
| `src/test/testData/` | Test workspace and recorded moon CLI output. Refer to [docs/development.md](docs/development.md). |
| `scripts/` | Shell scripts for development |
| `docs/` | Technical documentation (STE) |
| `docs/adr/` | Architecture decision records |
| `.claude/` | Claude Code configuration: settings, skills, subagents |
| `.github/` | CI workflows, issue templates, pull request template |
| `.vale/`, `.vale.ini` | Lint rules for Simplified Technical English |

## 3. Commands

Use the Gradle wrapper. Do not use a global Gradle installation.

| Task | Command |
|------|---------|
| Compile and run all tests | `./gradlew build` |
| Run the tests only | `./gradlew test` |
| Start a sandbox IDE with the plugin | `./gradlew runIde` |
| Do a check of compatibility with IDE versions | `./gradlew verifyPlugin` |
| Make the plugin ZIP file | `./gradlew buildPlugin` |
| Do a check of the documentation language | `vale .` |

The build uses JDK 21. Gradle gets JDK 21 automatically if it is not on the computer.

## 4. Code rules

1. Write all production code in Kotlin.
2. Use the package `io.github.artptr.moonexplorer` and subpackages.
3. Register all extensions in `plugin.xml`. Do not register extensions in code.
4. Put all user interface text in `MoonExplorerBundle.properties`. Do not write user interface text in code.
5. Do not block the event dispatch thread (EDT). Run moon CLI calls and file input/output on a background thread.
6. Use read actions to read the PSI or the virtual file system. Use write actions to change them.
7. Use Kotlin coroutines and services for background work. Do not create threads directly.
8. Add a test for each new function. Use `BasePlatformTestCase` for tests that need the IDE.
9. Do not add a dependency without a record in an issue or an ADR.
10. Before you push, run `./gradlew build`. The command must complete without errors.

For more information about the IntelliJ Platform, use the skill `intellij-plugin-dev`.

## 5. Documentation rules

1. Write all technical documentation in ASD-STE100 Simplified Technical English (STE).
2. Technical documentation includes all Markdown files, code comments, commit messages, and pull request text.
3. Files in the allowlist in [docs/ste/allowlist.md](docs/ste/allowlist.md) are exempt from STE. Write these files in standard English.
4. If you change the allowlist, also change the `[*.md]` exclusions in `.vale.ini`. The two lists must be the same.
5. Use the skill `ste-writing` when you write or change documentation.
6. Run `vale .` before you commit changes to documentation.

## 6. GitHub workflow

GitHub is the location for the code, the issues, the project board, and CI.

1. Each change starts from an issue. If there is no issue, create one.
2. Make a branch from `main` (maintainers and agents with write access), or from a fork (all other contributors). Use the name `<type>/<issue-number>-<short-description>`. Example: `feat/12-task-list`.
3. Use [Conventional Commits](https://www.conventionalcommits.org) for commit messages. Example: `feat: show moon projects in the tool window`.
4. Open a pull request into `main`. Put `Closes #<issue-number>` in the pull request text.
5. CI must pass before a merge. The workflows are `Build` and `Docs`. The `Build` workflow does not start for changes to documentation only.
6. Add an entry to the `Unreleased` section of `CHANGELOG.md` for each change that a user can see.
7. Do not push directly to `main`.

For labels and the project board, use the skill `github-flow`.

## 7. Safety rules for agents

1. Do not commit secrets, tokens, or signing keys.
2. Do not change `.github/workflows/release.yml` without approval from a maintainer.
3. Do not publish the plugin. Only a maintainer publishes releases.
4. If a task is not clear, ask a question before you start.
